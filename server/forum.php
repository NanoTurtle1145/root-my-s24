<?php
/**
 * RootMyS24 讨论区（网页版）
 *
 * 两层身份：
 *   1) 准入层 —— forum_gate.php 签发的短期票据（?t=…）；首次进来后下发同签名的会话
 *      cookie（滑动续期），所以 WebView 里逛久了不会被踢回口令页。
 *   2) 用户层 —— 用户名 + 密码的账号（forum_users）。登录后发言署名、可点赞、
 *      管理自己的帖子、有个人主页；未登录也能浏览与匿名发言。
 *
 * 数据：forum_users / forum_threads / forum_posts / forum_likes / forum_bans / forum_rate
 *      （forum_db.php 自动建表与迁移）
 * 本文件不含任何凭据：密钥在 forum_secret.php，数据库账号在 db_secret.php。
 */
declare(strict_types=1);
require __DIR__ . '/forum_db.php';

const F_SESSION_TTL   = 12 * 3600;      // 闸门会话 cookie
const F_USER_TTL      = 30 * 86400;     // 登录态 cookie
const F_PAGE_SIZE     = 20;             // 主题列表每页
const F_POSTS_PAGE    = 30;             // 主题内每页楼层

$secretFile = __DIR__ . '/forum_secret.php';
$secret = is_file($secretFile) ? (array)require $secretFile : [];
$hmacKey = (string)($secret['hmac_key'] ?? '');
$sessionTtl = (int)($secret['session_ttl'] ?? F_SESSION_TTL);
$nonce = rtrim(strtr(base64_encode(random_bytes(12)), '+/', '-_'), '=');
$cookiePath = dirname($_SERVER['SCRIPT_NAME'] ?? '/') ?: '/';
$isHttps = !empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off';

/** 签名/校验：base64url(payload).HMAC(payload) */
function f_sign(string $key, array $payload): string
{
    $json = json_encode($payload, JSON_UNESCAPED_UNICODE);
    return rtrim(strtr(base64_encode($json), '+/', '-_'), '=') . '.' . hash_hmac('sha256', $json, $key);
}

function f_verify(string $token, string $key): array
{
    if ($token === '' || !str_contains($token, '.')) {
        return [null, 'missing'];
    }
    [$b64, $sig] = explode('.', $token, 2);
    $pad = str_repeat('=', (4 - strlen($b64) % 4) % 4);
    $json = base64_decode(strtr($b64, '-_', '+/') . $pad, true);
    if ($json === false || !hash_equals(hash_hmac('sha256', $json, $key), $sig)) {
        return [null, 'bad'];
    }
    $data = json_decode($json, true);
    if (!is_array($data)) {
        return [null, 'bad'];
    }
    if ((int)($data['exp'] ?? 0) <= time()) {
        return [null, 'expired'];
    }
    return [$data, ''];
}

// ── 准入层 ────────────────────────────────────────────────────
$ticketRaw = (string)($_GET['t'] ?? $_POST['t'] ?? '');
$gateOk = false;
$reason = 'no_secret';
$install = '';

if ($hmacKey !== '') {
    [$payload, $reason] = f_verify($ticketRaw, $hmacKey);
    if ($payload === null) {
        [$payload, $reason] = f_verify((string)($_COOKIE['rms24f'] ?? ''), $hmacKey);
        if ($payload !== null) {
            $reason = '';
        }
    }
    if ($payload !== null) {
        $gateOk = true;
        $install = substr((string)($payload['i'] ?? ''), 0, 12);
    }
}

$csrf = $gateOk ? substr(hash_hmac('sha256', 'csrf|' . $install, $hmacKey), 0, 20) : '';
$ticketQs = $ticketRaw !== '' ? '&t=' . rawurlencode($ticketRaw) : '';

if (!headers_sent()) {
    header('Content-Type: text/html; charset=utf-8');
    header('Cache-Control: no-store');
    header('X-Frame-Options: DENY');
    header('X-Content-Type-Options: nosniff');
    header('Referrer-Policy: no-referrer');
    header(
        "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; img-src data:; "
        . "form-action 'self'; base-uri 'none'; script-src 'nonce-{$nonce}'"
    );
}

// ── 用户层 ────────────────────────────────────────────────────
$pdo = null;
$user = null;
$installBanned = null;
$userBanned = false;
$topics = forum_topics();

if ($gateOk) {
    try {
        $pdo = rms24_db();
        rms24_schema($pdo);

        // 会话 cookie 滑动续期
        setcookie('rms24f', f_sign($hmacKey, ['exp' => time() + $sessionTtl, 'i' => $install]), [
            'expires' => time() + $sessionTtl, 'path' => $cookiePath,
            'httponly' => true, 'secure' => $isHttps, 'samesite' => 'Lax',
        ]);

        [$u, ] = f_verify((string)($_COOKIE['rms24u'] ?? ''), $hmacKey);
        if ($u !== null) {
            $cand = forum_user($pdo, (int)($u['uid'] ?? 0));
            // token_version 对不上 = 已被强制下线（改密码/管理员踢出）
            if ($cand && (int)$cand['token_version'] === (int)($u['tv'] ?? -1)) {
                $user = $cand;
                $userBanned = (int)$user['banned'] === 1;
                if (strtotime((string)$user['last_seen']) < time() - 300) {
                    $pdo->prepare('UPDATE forum_users SET last_seen = ? WHERE id = ?')
                        ->execute([rms24_now(), (int)$user['id']]);
                }
            } else {
                setcookie('rms24u', '', ['expires' => time() - 3600, 'path' => $cookiePath]);
            }
        }

        $st = $pdo->prepare('SELECT reason FROM forum_bans WHERE install_hash = ? LIMIT 1');
        $st->execute([$install]);
        $row = $st->fetch();
        $installBanned = $row ? (string)$row['reason'] : null;
    } catch (Throwable $e) {
        $gateOk = false;
        $reason = 'db';
    }
}

$canPost = $gateOk && $installBanned === null && !$userBanned;

// ── 写操作 ────────────────────────────────────────────────────
if ($gateOk && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
    $action = (string)($_POST['action'] ?? '');
    $ipHash = substr(hash('sha256', (string)($_SERVER['REMOTE_ADDR'] ?? '') . '|rms24'), 0, 16);
    $back = static function (string $q) use ($ticketQs): void {
        header('Location: forum.php?' . $q . $ticketQs);
        exit;
    };

    // 登录/注册不需要 CSRF（此时还没有身份），其余动作都要
    $needCsrf = !in_array($action, ['login', 'register'], true);
    if ($needCsrf && !hash_equals($csrf, (string)($_POST['csrf'] ?? ''))) {
        $back('m=badcsrf');
    }

    // ── 注册 ──
    if ($action === 'register') {
        if (forum_rate_limit($pdo, 'reg_ip', $ipHash, 6, 3600)) {
            $back('v=register&m=ratelimited');
        }
        $name = forum_clean_line((string)($_POST['username'] ?? ''), 16);
        $pass = (string)($_POST['password'] ?? '');
        $pass2 = (string)($_POST['password2'] ?? '');
        $err = forum_username_error($name);
        if ($err !== '') {
            $back('v=register&m=' . urlencode($err));
        }
        if (strlen($pass) < 6 || strlen($pass) > 72) {
            $back('v=register&m=' . urlencode('密码需要 6–72 位'));
        }
        if ($pass !== $pass2) {
            $back('v=register&m=' . urlencode('两次输入的密码不一致'));
        }
        if (forum_user_by_name($pdo, $name)) {
            $back('v=register&m=' . urlencode('用户名已被占用'));
        }
        $now = rms24_now();
        // 第一个注册的账号给管理员角色，方便单人站长自用；其余一律普通用户
        $isFirstUser = (int)$pdo->query('SELECT COUNT(*) FROM forum_users')->fetchColumn() === 0;
        try {
            $pdo->prepare(
                'INSERT INTO forum_users (username, pass_hash, role, created_at, last_seen) VALUES (?, ?, ?, ?, ?)'
            )->execute([$name, password_hash($pass, PASSWORD_DEFAULT), $isFirstUser ? 'admin' : 'user', $now, $now]);
        } catch (Throwable $e) {
            $back('v=register&m=' . urlencode('注册失败，请换个用户名'));
        }
        $uid = (int)$pdo->lastInsertId();
        $fresh = forum_user($pdo, $uid);
        setcookie('rms24u', f_sign($hmacKey, ['uid' => $uid, 'tv' => (int)$fresh['token_version'], 'exp' => time() + F_USER_TTL]), [
            'expires' => time() + F_USER_TTL, 'path' => $cookiePath,
            'httponly' => true, 'secure' => $isHttps, 'samesite' => 'Lax',
        ]);
        $back('v=me&m=welcome');
    }

    // ── 登录 ──
    if ($action === 'login') {
        $name = forum_clean_line((string)($_POST['username'] ?? ''), 16);
        $pass = (string)($_POST['password'] ?? '');
        if (forum_rate_limit($pdo, 'login_ip', $ipHash, 20, 900)
            || forum_rate_limit($pdo, 'login_user', strtolower($name), 10, 900)) {
            $back('v=login&m=' . urlencode('尝试过于频繁，请稍后再试'));
        }
        $cand = forum_user_by_name($pdo, $name);
        if (!$cand || !password_verify($pass, (string)$cand['pass_hash'])) {
            $back('v=login&m=' . urlencode('用户名或密码不正确'));
        }
        // 封禁账号直接拒绝登录（未登录仍可浏览），并把理由告诉本人
        if ((int)$cand['banned'] === 1) {
            $why = trim((string)$cand['ban_reason']);
            $back('v=login&m=' . urlencode('该账号已被封禁' . ($why !== '' ? '：' . $why : '')));
        }
        $pdo->prepare('UPDATE forum_users SET last_seen = ? WHERE id = ?')->execute([rms24_now(), (int)$cand['id']]);
        setcookie('rms24u', f_sign($hmacKey, ['uid' => (int)$cand['id'], 'tv' => (int)$cand['token_version'], 'exp' => time() + F_USER_TTL]), [
            'expires' => time() + F_USER_TTL, 'path' => $cookiePath,
            'httponly' => true, 'secure' => $isHttps, 'samesite' => 'Lax',
        ]);
        $back('m=login');
    }

    // ── 退出 ──
    if ($action === 'logout') {
        setcookie('rms24u', '', ['expires' => time() - 3600, 'path' => $cookiePath]);
        $back('m=logout');
    }

    if (!$canPost) {
        $back('m=banned');
    }

    // ── 发主题 ──
    if ($action === 'new_thread') {
        $title = forum_clean_line((string)($_POST['title'] ?? ''), 80);
        $body = forum_clean_text((string)($_POST['body'] ?? ''), 8000);
        $topic = (string)($_POST['topic'] ?? 'discuss');
        if (!isset($topics[$topic])) {
            $topic = 'other';
        }
        if (mb_strlen($title) < 4) {
            $back('m=' . urlencode('标题至少 4 个字'));
        }
        if (mb_strlen($body) < 4) {
            $back('m=' . urlencode('正文至少 4 个字'));
        }
        if (forum_too_fast($pdo, $install, 20, (int)($user['id'] ?? 0))) {
            $back('m=toofast');
        }
        if (forum_rate_limit($pdo, 'thread', $install, 8, 3600)
            || forum_rate_limit($pdo, 'thread_ip', $ipHash, 25, 3600)) {
            $back('m=ratelimited');
        }
        $now = rms24_now();
        $pdo->beginTransaction();
        $pdo->prepare(
            'INSERT INTO forum_threads (topic, title, install_hash, user_id, nickname, posts, created_at, updated_at)
             VALUES (?, ?, ?, ?, ?, 1, ?, ?)'
        )->execute([$topic, $title, $install, $user['id'] ?? null, (string)($user['username'] ?? ''), $now, $now]);
        $newId = (int)$pdo->lastInsertId();
        $pdo->prepare(
            'INSERT INTO forum_posts (thread_id, install_hash, user_id, nickname, body, ip_hash, created_at)
             VALUES (?, ?, ?, ?, ?, ?, ?)'
        )->execute([$newId, $install, $user['id'] ?? null, (string)($user['username'] ?? ''), $body, $ipHash, $now]);
        if ($user) {
            $pdo->prepare('UPDATE forum_users SET threads = threads + 1, posts = posts + 1 WHERE id = ?')->execute([(int)$user['id']]);
        }
        $pdo->commit();
        $back('v=t&id=' . $newId . '&m=posted');
    }

    // ── 回复 ──
    if ($action === 'reply') {
        $threadId = (int)($_POST['thread_id'] ?? 0);
        $body = forum_clean_text((string)($_POST['body'] ?? ''), 8000);
        $st = $pdo->prepare('SELECT locked, hidden FROM forum_threads WHERE id = ? LIMIT 1');
        $st->execute([$threadId]);
        $thread = $st->fetch();
        if (!$thread || (int)$thread['hidden'] === 1) {
            $back('m=notfound');
        }
        if ((int)$thread['locked'] === 1) {
            $back('v=t&id=' . $threadId . '&m=locked');
        }
        if (mb_strlen($body) < 2) {
            $back('v=t&id=' . $threadId . '&m=' . urlencode('回复至少 2 个字'));
        }
        if (forum_too_fast($pdo, $install, 15, (int)($user['id'] ?? 0))) {
            $back('v=t&id=' . $threadId . '&m=toofast');
        }
        if (forum_rate_limit($pdo, 'reply', $install, 60, 3600)
            || forum_rate_limit($pdo, 'reply_ip', $ipHash, 120, 3600)) {
            $back('v=t&id=' . $threadId . '&m=ratelimited');
        }
        $now = rms24_now();
        $pdo->prepare(
            'INSERT INTO forum_posts (thread_id, install_hash, user_id, nickname, body, ip_hash, created_at)
             VALUES (?, ?, ?, ?, ?, ?, ?)'
        )->execute([$threadId, $install, $user['id'] ?? null, (string)($user['username'] ?? ''), $body, $ipHash, $now]);
        $pdo->prepare('UPDATE forum_threads SET posts = posts + 1, updated_at = ? WHERE id = ?')->execute([$now, $threadId]);
        if ($user) {
            $pdo->prepare('UPDATE forum_users SET posts = posts + 1 WHERE id = ?')->execute([(int)$user['id']]);
        }
        $st = $pdo->prepare('SELECT COUNT(*) FROM forum_posts WHERE thread_id = ? AND hidden = 0');
        $st->execute([$threadId]);
        $lastPage = max(1, (int)ceil((int)$st->fetchColumn() / F_POSTS_PAGE));
        $back('v=t&id=' . $threadId . '&p=' . $lastPage . '&m=replied#bottom');
    }

    // ── 点赞（需登录） ──
    if ($action === 'like') {
        $pid = (int)($_POST['post_id'] ?? 0);
        if (!$user) {
            $back('v=t&id=' . (int)($_POST['thread_id'] ?? 0) . '&m=' . urlencode('点赞需要先登录'));
        }
        $st = $pdo->prepare('SELECT id, thread_id, user_id FROM forum_posts WHERE id = ? AND hidden = 0 LIMIT 1');
        $st->execute([$pid]);
        $post = $st->fetch();
        if (!$post) {
            $back('m=notfound');
        }
        $has = $pdo->prepare('SELECT 1 FROM forum_likes WHERE post_id = ? AND user_id = ? LIMIT 1');
        $has->execute([$pid, (int)$user['id']]);
        if ($has->fetchColumn()) {
            $pdo->prepare('DELETE FROM forum_likes WHERE post_id = ? AND user_id = ?')->execute([$pid, (int)$user['id']]);
            $pdo->prepare('UPDATE forum_posts SET likes = GREATEST(likes - 1, 0) WHERE id = ?')->execute([$pid]);
            if ((int)$post['user_id'] > 0) {
                $pdo->prepare('UPDATE forum_users SET likes_got = GREATEST(likes_got - 1, 0) WHERE id = ?')->execute([(int)$post['user_id']]);
            }
        } else {
            $pdo->prepare('INSERT INTO forum_likes (post_id, user_id, created_at) VALUES (?, ?, ?)')
                ->execute([$pid, (int)$user['id'], rms24_now()]);
            $pdo->prepare('UPDATE forum_posts SET likes = likes + 1 WHERE id = ?')->execute([$pid]);
            if ((int)$post['user_id'] > 0) {
                $pdo->prepare('UPDATE forum_users SET likes_got = likes_got + 1 WHERE id = ?')->execute([(int)$post['user_id']]);
            }
        }
        $back('v=t&id=' . (int)$post['thread_id'] . '&m=liked#p' . $pid);
    }

    // ── 删自己的帖子/主题（软隐藏） ──
    if ($action === 'del_own_post' && $user) {
        $pid = (int)($_POST['post_id'] ?? 0);
        $st = $pdo->prepare('SELECT id, thread_id FROM forum_posts WHERE id = ? AND user_id = ? LIMIT 1');
        $st->execute([$pid, (int)$user['id']]);
        $post = $st->fetch();
        if (!$post) {
            $back('m=' . urlencode('只能删除自己的回复'));
        }
        $pdo->prepare('UPDATE forum_posts SET hidden = 1 WHERE id = ?')->execute([$pid]);
        $pdo->prepare('UPDATE forum_threads SET posts = GREATEST(posts - 1, 1) WHERE id = ?')->execute([(int)$post['thread_id']]);
        $back('v=t&id=' . (int)$post['thread_id'] . '&m=' . urlencode('已删除你的回复'));
    }

    if ($action === 'del_own_thread' && $user) {
        $tid = (int)($_POST['thread_id'] ?? 0);
        $st = $pdo->prepare('SELECT id FROM forum_threads WHERE id = ? AND user_id = ? LIMIT 1');
        $st->execute([$tid, (int)$user['id']]);
        if (!$st->fetch()) {
            $back('m=' . urlencode('只能删除自己的主题'));
        }
        $pdo->prepare('UPDATE forum_threads SET hidden = 1 WHERE id = ?')->execute([$tid]);
        $pdo->prepare('UPDATE forum_posts SET hidden = 1 WHERE thread_id = ?')->execute([$tid]);
        $back('m=' . urlencode('已删除你的主题'));
    }

    // ── 资料与密码 ──
    if ($action === 'save_profile' && $user) {
        $bio = forum_clean_line((string)($_POST['bio'] ?? ''), 140);
        $pdo->prepare('UPDATE forum_users SET bio = ? WHERE id = ?')->execute([$bio, (int)$user['id']]);
        $back('v=me&m=' . urlencode('资料已保存'));
    }

    if ($action === 'save_password' && $user) {
        $old = (string)($_POST['old_password'] ?? '');
        $new = (string)($_POST['new_password'] ?? '');
        if (!password_verify($old, (string)$user['pass_hash'])) {
            $back('v=me&m=' . urlencode('当前密码不正确'));
        }
        if (strlen($new) < 6 || strlen($new) > 72) {
            $back('v=me&m=' . urlencode('新密码需要 6–72 位'));
        }
        // 改密码 = 递增 token_version，其它设备上的登录态立即失效
        $pdo->prepare('UPDATE forum_users SET pass_hash = ?, token_version = token_version + 1 WHERE id = ?')
            ->execute([password_hash($new, PASSWORD_DEFAULT), (int)$user['id']]);
        $fresh = forum_user($pdo, (int)$user['id']);
        setcookie('rms24u', f_sign($hmacKey, ['uid' => (int)$user['id'], 'tv' => (int)$fresh['token_version'], 'exp' => time() + F_USER_TTL]), [
            'expires' => time() + F_USER_TTL, 'path' => $cookiePath,
            'httponly' => true, 'secure' => $isHttps, 'samesite' => 'Lax',
        ]);
        $back('v=me&m=' . urlencode('密码已更新，其它设备已退出登录'));
    }

    $back('m=badaction');
}

// ── 读视图 ────────────────────────────────────────────────────
$view = (string)($_GET['v'] ?? 'list');
if ($view === 'me' && $user === null) {
    $view = 'login';
}
$page = max(1, (int)($_GET['p'] ?? 1));
$cat = (string)($_GET['cat'] ?? '');
$q = forum_clean_line((string)($_GET['q'] ?? ''), 40);
$sort = (string)($_GET['sort'] ?? 'active');
if (!in_array($sort, ['active', 'new', 'hot'], true)) {
    $sort = 'active';
}

$threads = [];
$thread = null;
$posts = [];
$profile = null;
$profileThreads = [];
$profilePosts = [];
$likedPosts = [];
$counts = ['threads' => 0, 'posts' => 0, 'users' => 0];
$catCounts = [];
$totalRows = 0;
$pages = 1;
$postPages = 1;
$meThreads = [];
$mePosts = [];

if ($gateOk) {
    $counts['threads'] = (int)$pdo->query('SELECT COUNT(*) FROM forum_threads WHERE hidden = 0')->fetchColumn();
    $counts['posts'] = (int)$pdo->query('SELECT COUNT(*) FROM forum_posts WHERE hidden = 0')->fetchColumn();
    $counts['users'] = (int)$pdo->query('SELECT COUNT(*) FROM forum_users')->fetchColumn();
    foreach ($pdo->query('SELECT topic, COUNT(*) c FROM forum_threads WHERE hidden = 0 GROUP BY topic') as $r) {
        $catCounts[(string)$r['topic']] = (int)$r['c'];
    }

    if ($view === 't') {
        $id = (int)($_GET['id'] ?? 0);
        $st = $pdo->prepare(
            'SELECT t.*, u.username AS author FROM forum_threads t
             LEFT JOIN forum_users u ON u.id = t.user_id
             WHERE t.id = ? AND t.hidden = 0 LIMIT 1'
        );
        $st->execute([$id]);
        $thread = $st->fetch() ?: null;
        if ($thread) {
            $pdo->prepare('UPDATE forum_threads SET views = views + 1 WHERE id = ?')->execute([$id]);
            $cnt = $pdo->prepare('SELECT COUNT(*) FROM forum_posts WHERE thread_id = ? AND hidden = 0');
            $cnt->execute([$id]);
            $postPages = max(1, (int)ceil(max(1, (int)$cnt->fetchColumn()) / F_POSTS_PAGE));
            $page = min($page, $postPages);
            $st = $pdo->prepare(
                'SELECT p.*, u.username AS author FROM forum_posts p
                 LEFT JOIN forum_users u ON u.id = p.user_id
                 WHERE p.thread_id = ? AND p.hidden = 0 ORDER BY p.id ASC
                 LIMIT ' . F_POSTS_PAGE . ' OFFSET ' . (($page - 1) * F_POSTS_PAGE)
            );
            $st->execute([$id]);
            $posts = $st->fetchAll();
            if ($user) {
                $st = $pdo->prepare('SELECT post_id FROM forum_likes WHERE user_id = ? AND post_id IN ('
                    . implode(',', array_map(static fn($p) => (int)$p['id'], $posts ?: [['id' => 0]])) . ')');
                $st->execute([(int)$user['id']]);
                foreach ($st->fetchAll(PDO::FETCH_COLUMN) as $pid) {
                    $likedPosts[(int)$pid] = true;
                }
            }
        }
    } elseif ($view === 'u' || $view === 'me') {
        $profile = $view === 'me' ? $user : forum_user($pdo, (int)($_GET['id'] ?? 0));
        if ($profile) {
            $st = $pdo->prepare(
                'SELECT t.*, (SELECT COUNT(*) FROM forum_posts p WHERE p.thread_id = t.id AND p.hidden = 0) AS replies
                 FROM forum_threads t WHERE t.user_id = ? AND t.hidden = 0 ORDER BY t.updated_at DESC LIMIT 10'
            );
            $st->execute([(int)$profile['id']]);
            $profileThreads = $st->fetchAll();
            $st = $pdo->prepare(
                'SELECT p.*, t.title AS thread_title FROM forum_posts p
                 JOIN forum_threads t ON t.id = p.thread_id
                 WHERE p.user_id = ? AND p.hidden = 0 AND t.hidden = 0 ORDER BY p.id DESC LIMIT 10'
            );
            $st->execute([(int)$profile['id']]);
            $profilePosts = $st->fetchAll();
        }
    } elseif ($view === 'login' || $view === 'register') {
        // 表单页，无需查询
    } else {
        $view = 'list';
        $where = ['t.hidden = 0'];
        $args = [];
        if ($cat !== '' && isset($topics[$cat])) {
            $where[] = 't.topic = ?';
            $args[] = $cat;
        }
        if ($q !== '') {
            $where[] = '(t.title LIKE ? OR EXISTS (SELECT 1 FROM forum_posts p WHERE p.thread_id = t.id AND p.body LIKE ?))';
            $args[] = '%' . $q . '%';
            $args[] = '%' . $q . '%';
        }
        $ws = ' WHERE ' . implode(' AND ', $where);
        $order = [
            'active' => 't.pinned DESC, t.updated_at DESC',
            'new'    => 't.pinned DESC, t.created_at DESC',
            'hot'    => 't.pinned DESC, (t.views + t.posts * 4) DESC, t.updated_at DESC',
        ][$sort];

        $st = $pdo->prepare('SELECT COUNT(*) FROM forum_threads t' . $ws);
        $st->execute($args);
        $totalRows = (int)$st->fetchColumn();
        $pages = max(1, (int)ceil($totalRows / F_PAGE_SIZE));
        $page = min($page, $pages);
        $st = $pdo->prepare(
            'SELECT t.*, u.username AS author, u.last_seen AS author_seen,
                    (SELECT COUNT(*) FROM forum_posts p WHERE p.thread_id = t.id AND p.hidden = 0) AS replies
             FROM forum_threads t LEFT JOIN forum_users u ON u.id = t.user_id'
            . $ws . ' ORDER BY ' . $order . ' LIMIT ' . F_PAGE_SIZE . ' OFFSET ' . (($page - 1) * F_PAGE_SIZE)
        );
        $st->execute($args);
        $threads = $st->fetchAll();
    }
}

$flashMap = [
    'posted' => ['ok', '已发布，感谢分享。'], 'replied' => ['ok', '回复已发布。'],
    'login' => ['ok', '已登录。'], 'logout' => ['ok', '已退出登录。'],
    'welcome' => ['ok', '注册成功，欢迎加入！'], 'liked' => ['ok', '已更新点赞。'],
    'toofast' => ['err', '发得太快了，请稍等十几秒。'],
    'ratelimited' => ['err', '一小时内的操作次数已达上限，请稍后再来。'],
    'locked' => ['err', '该主题已被锁定，不能回复。'],
    'banned' => ['err', '当前账号或标识已被限制发言。'],
    'notfound' => ['err', '主题不存在或已删除。'],
    'badcsrf' => ['err', '页面已过期，请刷新后重试。'],
    'badaction' => ['err', '无法识别的操作。'],
];
$m = (string)($_GET['m'] ?? '');
$flash = $flashErr = '';
if ($m !== '') {
    if (isset($flashMap[$m])) {
        [$kind, $text] = $flashMap[$m];
        $kind === 'ok' ? $flash = $text : $flashErr = $text;
    } else {
        $flashErr = $m;   // 动态文案（注册校验等）
    }
}
?><!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="color-scheme" content="light dark">
<title><?= $thread ? fh((string)$thread['title']) . ' · ' : '' ?>RootMyS24 讨论区</title>
<style>
  :root{
    --bg:#f5f6f8; --card:#fff; --card2:#fafbfc; --line:#e3e6ec; --line2:#eef1f5;
    --fg:#141922; --dim:#667085; --accent:#2563eb; --accent-soft:#eaf1ff; --accent-fg:#fff;
    --ok:#08875d; --err:#d92d20; --warn:#b45309; --code:#f4f6f8;
    --radius:16px; --radius-sm:11px; --shadow:0 1px 2px #1018280d,0 10px 26px #1018280a;
    --shadow-hi:0 2px 4px #10182812,0 16px 40px #10182814;
  }
  @media (prefers-color-scheme:dark){
    :root{
      --bg:#0d1015; --card:#151a21; --card2:#12171e; --line:#242b35; --line2:#1d232c;
      --fg:#e7eaf0; --dim:#98a2b3; --accent:#63a4ff; --accent-soft:#16243a; --accent-fg:#0b1220;
      --ok:#3ddc97; --err:#ff8078; --warn:#ffc46b; --code:#0f1319;
      --shadow:0 1px 2px #0006,0 12px 30px #0005; --shadow-hi:0 2px 6px #0008,0 18px 44px #0006;
    }
  }
  *{box-sizing:border-box}
  html{-webkit-text-size-adjust:100%;scroll-behavior:smooth;scroll-padding-top:70px}
  body{margin:0;background:var(--bg);color:var(--fg);
    font:15px/1.7 system-ui,-apple-system,"Segoe UI","PingFang SC","Noto Sans CJK SC","Microsoft YaHei",sans-serif;
    padding-bottom:calc(44px + env(safe-area-inset-bottom))}
  a{color:var(--accent);text-decoration:none} a:hover{text-decoration:underline}
  .wrap{max-width:860px;margin:0 auto;padding:0 14px}
  .row-between{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
  .sp{flex:1}

  /* 顶栏 */
  .top{position:sticky;top:0;z-index:20;background:color-mix(in srgb,var(--bg) 86%,transparent);
       backdrop-filter:saturate(1.7) blur(12px);border-bottom:1px solid var(--line);
       padding:calc(9px + env(safe-area-inset-top)) 0 9px}
  .brand{display:flex;align-items:center;gap:9px;font-weight:750;font-size:15.5px}
  .brand:hover{text-decoration:none}
  .brand .mark{width:26px;height:26px;border-radius:9px;display:grid;place-items:center;
       background:linear-gradient(135deg,var(--accent),color-mix(in srgb,var(--accent) 55%,#8b5cf6));
       color:#fff;font-size:13px;font-weight:800;letter-spacing:-.5px}
  .brand .sub{color:var(--dim);font-weight:500;font-size:12px}
  .chip{display:inline-flex;align-items:center;gap:7px;padding:5px 10px;border-radius:999px;
       border:1px solid var(--line);background:var(--card);font-size:12.5px;color:var(--fg)}
  .chip:hover{border-color:var(--accent);text-decoration:none}
  .avatar{width:30px;height:30px;flex:0 0 30px;border-radius:50%;display:grid;place-items:center;
       font-size:11.5px;font-weight:750;
       background:hsl(var(--h,214) 72% 50% / .16);color:hsl(var(--h,214) 72% 42%);border:1px solid hsl(var(--h,214) 72% 50% / .28)}
  @media (prefers-color-scheme:dark){ .avatar{background:hsl(var(--h,214) 72% 60% / .18);color:hsl(var(--h,214) 80% 74%)} }
  .avatar.sm{width:24px;height:24px;flex:0 0 24px;font-size:10px}
  .avatar.lg{width:56px;height:56px;flex:0 0 56px;font-size:19px;border-radius:18px}

  .btn{display:inline-flex;align-items:center;gap:6px;padding:8px 14px;border-radius:var(--radius-sm);
       border:1px solid var(--accent);background:var(--accent);color:#fff;font-weight:650;font-size:13.5px;cursor:pointer}
  .btn:hover{filter:brightness(1.07);text-decoration:none}
  .btn.ghost{background:var(--card);color:var(--fg);border-color:var(--line)}
  .btn.ghost:hover{border-color:var(--accent);color:var(--accent);filter:none}
  .btn.sm{padding:5px 10px;font-size:12.5px}
  .btn.danger{background:transparent;color:var(--err);border-color:color-mix(in srgb,var(--err) 45%,var(--line))}
  .btn.danger:hover{background:color-mix(in srgb,var(--err) 12%,transparent);filter:none}

  main{padding-top:18px}
  .flash{margin:0 0 14px;padding:11px 14px;border-radius:var(--radius-sm);font-size:13.5px;border:1px solid;
       display:flex;gap:8px;align-items:flex-start}
  .flash.ok{background:color-mix(in srgb,var(--ok) 10%,transparent);border-color:color-mix(in srgb,var(--ok) 38%,transparent);color:var(--ok)}
  .flash.err{background:color-mix(in srgb,var(--err) 10%,transparent);border-color:color-mix(in srgb,var(--err) 38%,transparent);color:var(--err)}

  /* 概览 + 工具条 */
  .hero{background:linear-gradient(180deg,color-mix(in srgb,var(--accent) 8%,transparent),transparent);
       border:1px solid var(--line);border-radius:var(--radius);padding:16px 16px 14px;margin-bottom:14px;box-shadow:var(--shadow)}
  .hero h1{margin:0 0 4px;font-size:18px;letter-spacing:-.2px}
  .hero p{margin:0;color:var(--dim);font-size:13px}
  .hero .kpis{display:flex;gap:16px;margin-top:12px;flex-wrap:wrap}
  .kpi{font-size:12.5px;color:var(--dim)}
  .kpi b{display:block;font-size:17px;color:var(--fg);font-weight:750;line-height:1.3}

  .toolbar{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin-bottom:12px}
  .tools{display:flex;gap:6px;overflow-x:auto;padding-bottom:2px;scrollbar-width:none}
  .tools::-webkit-scrollbar{display:none}
  .tools a{padding:6px 12px;border-radius:999px;border:1px solid var(--line);background:var(--card);
       font-size:12.5px;color:var(--dim);white-space:nowrap}
  .tools a:hover{border-color:var(--accent);color:var(--accent);text-decoration:none}
  .tools a.on{background:var(--accent);border-color:var(--accent);color:#fff;font-weight:650}
  .tools a .n{opacity:.7;font-size:11.5px;margin-left:4px}
  form.search{display:flex;gap:6px;flex:1;min-width:200px}
  input[type=text],input[type=password],input[type=search],select,textarea{
       width:100%;padding:9px 12px;border-radius:var(--radius-sm);border:1px solid var(--line);
       background:var(--card);color:var(--fg);font:inherit;font-size:14px}
  input:focus,select:focus,textarea:focus{outline:2px solid color-mix(in srgb,var(--accent) 42%,transparent);outline-offset:1px;border-color:var(--accent)}
  label.f{display:block;font-size:12.5px;color:var(--dim);margin:0 0 5px}
  .grid{display:grid;gap:10px;grid-template-columns:1fr}
  @media(min-width:640px){ .grid.two{grid-template-columns:1fr 1fr} }

  /* 主题列表 */
  ul.threads{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:10px}
  ul.threads li{display:flex;gap:12px;background:var(--card);border:1px solid var(--line);
       border-radius:var(--radius);padding:13px 15px;box-shadow:var(--shadow);transition:.16s}
  ul.threads li:hover{box-shadow:var(--shadow-hi);transform:translateY(-1px);border-color:color-mix(in srgb,var(--accent) 30%,var(--line))}
  ul.threads li.pin{border-color:color-mix(in srgb,var(--warn) 45%,var(--line));background:linear-gradient(90deg,color-mix(in srgb,var(--warn) 7%,transparent),transparent 45%),var(--card)}
  .t-main{flex:1;min-width:0}
  .t-top{display:flex;gap:7px;align-items:center;flex-wrap:wrap;margin-bottom:3px}
  .t-title{font-weight:680;font-size:15.5px;line-height:1.45;display:block;margin-bottom:3px}
  .t-title:hover{text-decoration:none;color:var(--accent)}
  .t-meta{color:var(--dim);font-size:12.5px;display:flex;gap:11px;flex-wrap:wrap;align-items:center}
  .t-stats{display:flex;flex-direction:column;align-items:flex-end;gap:4px;justify-content:center;text-align:right}
  .t-stats .num{font-size:15px;font-weight:750}
  .t-stats .lbl{font-size:11px;color:var(--dim)}

  .badge{display:inline-block;padding:1.5px 9px;border-radius:999px;font-size:11.5px;border:1px solid var(--line);color:var(--dim);white-space:nowrap}
  .badge.topic{color:hsl(var(--h,214) 70% 40%);border-color:hsl(var(--h,214) 70% 50% / .35);background:hsl(var(--h,214) 70% 50% / .11)}
  @media (prefers-color-scheme:dark){ .badge.topic{color:hsl(var(--h,214) 80% 74%)} }
  .badge.pin{color:var(--warn);border-color:color-mix(in srgb,var(--warn) 40%,var(--line));background:color-mix(in srgb,var(--warn) 12%,transparent)}
  .badge.role{color:var(--accent);border-color:color-mix(in srgb,var(--accent) 40%,var(--line));background:var(--accent-soft)}

  /* 主题页 */
  .crumbs{font-size:12.5px;color:var(--dim);margin:2px 0 10px;display:flex;gap:7px;align-items:center;flex-wrap:wrap}
  .thread-title{font-size:20px;font-weight:750;line-height:1.4;margin:0 0 8px;letter-spacing:-.2px}
  .post{background:var(--card);border:1px solid var(--line);border-radius:var(--radius);padding:14px 16px;margin-bottom:12px;box-shadow:var(--shadow)}
  .post.op{border-color:color-mix(in srgb,var(--accent) 26%,var(--line))}
  .post:target{border-color:var(--accent);box-shadow:var(--shadow-hi)}
  .p-head{display:flex;align-items:center;gap:10px;margin-bottom:10px}
  .p-who{font-size:13.5px;font-weight:700}
  .p-time{color:var(--dim);font-size:12px}
  .p-actions{display:flex;gap:8px;align-items:center;margin-top:11px;padding-top:9px;border-top:1px solid var(--line2);flex-wrap:wrap}
  .p-actions form{display:inline}
  .like{display:inline-flex;align-items:center;gap:5px;padding:4px 11px;border-radius:999px;
       border:1px solid var(--line);background:transparent;color:var(--dim);font-size:12.5px;cursor:pointer}
  .like:hover{border-color:var(--accent);color:var(--accent)}
  .like.on{border-color:color-mix(in srgb,var(--accent) 45%,var(--line));background:var(--accent-soft);color:var(--accent);font-weight:650}
  .p-body{word-break:break-word;overflow-wrap:anywhere;font-size:14.6px;line-height:1.75}
  .p-body a{word-break:break-all}
  .p-body code{background:var(--code);border:1px solid var(--line);border-radius:6px;padding:1px 6px;
       font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12.8px}
  .p-body blockquote{margin:8px 0;padding:2px 0 2px 12px;border-left:3px solid var(--line);color:var(--dim)}
  .codeblock{border:1px solid var(--line);border-radius:var(--radius-sm);overflow:hidden;margin:10px 0;background:var(--code)}
  .code-head{display:flex;align-items:center;gap:8px;padding:6px 10px;border-bottom:1px solid var(--line);
       background:color-mix(in srgb,var(--line) 25%,transparent);font-size:11.5px;color:var(--dim)}
  .code-lang{font-family:ui-monospace,Menlo,monospace}
  .code-head .copy{margin-left:auto;border:1px solid var(--line);background:var(--card);color:var(--dim);
       border-radius:7px;padding:2px 9px;font-size:11.5px;cursor:pointer}
  .code-head .copy:hover{color:var(--accent);border-color:var(--accent)}
  .codeblock pre{margin:0;padding:12px 13px;overflow:auto;font-size:12.7px;line-height:1.6}
  .codeblock code{background:none;border:0;padding:0}

  form.compose{background:var(--card);border:1px solid var(--line);border-radius:var(--radius);padding:15px 16px;box-shadow:var(--shadow)}
  form.compose.hi{border-color:color-mix(in srgb,var(--accent) 34%,var(--line))}
  textarea{min-height:118px;resize:vertical;line-height:1.7}
  .actions{display:flex;align-items:center;gap:12px;margin-top:12px;flex-wrap:wrap}
  .hint{color:var(--dim);font-size:12.5px}
  details.new{margin-bottom:14px}
  details.new>summary{list-style:none;cursor:pointer;display:inline-flex;align-items:center;gap:7px;
       padding:8px 14px;border-radius:var(--radius-sm);border:1px solid var(--line);background:var(--card);
       font-size:13.5px;font-weight:650;box-shadow:var(--shadow)}
  details.new>summary::-webkit-details-marker{display:none}
  details.new>summary:hover{border-color:var(--accent);color:var(--accent)}
  details.new[open]>summary{margin-bottom:12px}
  .fmt-help{display:flex;gap:10px;flex-wrap:wrap;color:var(--dim);font-size:11.5px;margin-top:7px}
  .fmt-help code{background:var(--code);border:1px solid var(--line);border-radius:5px;padding:0 4px}

  /* 个人主页 / 设置 */
  .profile{background:var(--card);border:1px solid var(--line);border-radius:var(--radius);padding:18px;
       box-shadow:var(--shadow);display:flex;gap:15px;align-items:center;flex-wrap:wrap;margin-bottom:14px}
  .profile .who{min-width:180px;flex:1}
  .profile .name{font-size:18px;font-weight:750;display:flex;align-items:center;gap:8px;flex-wrap:wrap}
  .profile .bio{color:var(--dim);font-size:13px;margin-top:4px}
  .stats{display:flex;gap:18px;flex-wrap:wrap;margin-top:8px}
  .stats div{font-size:12.5px;color:var(--dim)}
  .stats b{display:block;font-size:16px;color:var(--fg)}
  .card-sec{background:var(--card);border:1px solid var(--line);border-radius:var(--radius);padding:15px 16px;
       box-shadow:var(--shadow);margin-bottom:12px}
  .card-sec h3{margin:0 0 10px;font-size:14px;font-weight:700;display:flex;align-items:center;gap:8px}
  .card-sec h3::before{content:"";width:4px;height:14px;border-radius:2px;background:var(--accent)}
  .mini{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:8px}
  .mini li{padding-bottom:8px;border-bottom:1px solid var(--line2);font-size:13.5px}
  .mini li:last-child{border-bottom:0;padding-bottom:0}
  .mini .meta{color:var(--dim);font-size:12px;margin-top:2px}
  .empty{text-align:center;color:var(--dim);padding:36px 16px;background:var(--card);border:1px dashed var(--line);border-radius:var(--radius)}
  .empty strong{display:block;color:var(--fg);font-size:15px;margin-bottom:6px}
  .pager{display:flex;align-items:center;gap:12px;justify-content:center;margin:18px 0;color:var(--dim);font-size:13px;flex-wrap:wrap}
  .pager a{padding:6px 12px;border-radius:999px;border:1px solid var(--line);background:var(--card);color:var(--fg)}
  .pager a:hover{border-color:var(--accent);color:var(--accent);text-decoration:none}
  footer{margin-top:28px;padding-top:15px;border-top:1px solid var(--line);color:var(--dim);font-size:12.5px}
  .tabs{display:flex;gap:6px;margin-bottom:14px}
  .tabs a{padding:6px 12px;border-radius:999px;border:1px solid var(--line);font-size:12.5px;color:var(--dim);background:var(--card)}
  .tabs a.on{background:var(--accent);border-color:var(--accent);color:#fff;font-weight:650}
  .tabs a:hover{text-decoration:none;border-color:var(--accent);color:var(--accent)}
  .tabs a.on:hover{color:#fff}
  .deny{max-width:520px;margin:12vh auto;background:var(--card);border:1px solid var(--line);border-radius:20px;
        padding:28px 26px;box-shadow:var(--shadow-hi);text-align:center}
  .deny .mark{width:44px;height:44px;border-radius:14px;margin:0 auto 12px;display:grid;place-items:center;
        background:var(--accent-soft);color:var(--accent);font-size:20px}
  .deny h1{font-size:17px;margin:0 0 8px}
  .deny p{color:var(--dim);font-size:13.5px;margin:6px 0 0}
  .toplink{position:fixed;right:16px;bottom:calc(18px + env(safe-area-inset-bottom));z-index:15;
        width:38px;height:38px;border-radius:50%;border:1px solid var(--line);background:var(--card);
        color:var(--dim);display:none;place-items:center;box-shadow:var(--shadow);cursor:pointer}
  .toplink.show{display:grid}
  @media (max-width:640px){
    .hero{padding:14px}
    ul.threads li{padding:12px}
    .t-stats{flex-direction:row;gap:10px;align-items:center}
    .thread-title{font-size:18.5px}
  }
</style>
</head>
<body>

<?php if (!$gateOk): ?>
  <div class="deny">
    <div class="mark">🔒</div>
    <h1>无法进入讨论区</h1>
    <p>
      <?php
        echo match ($reason) {
            'expired'   => '票据已过期，请在 App 的「关于 → 讨论区」重新输入口令。',
            'db'        => '服务端数据库暂时不可用，请稍后再试。',
            'no_secret' => '服务端尚未完成配置（forum_secret.php）。',
            default     => '票据无效或缺失，请从 App 的「关于 → 讨论区」进入。',
        };
      ?>
    </p>
    <p>讨论区仅对持有口令的用户开放；口令在服务端校验，客户端不留任何秘密。</p>
  </div>
</body></html>
<?php exit; endif; ?>

<?php
  $meChip = $user
      ? ['href' => 'forum.php?v=me' . $ticketQs, 'label' => (string)$user['username'], 'hue' => forum_avatar((string)$user['username'])[0], 'initials' => forum_avatar((string)$user['username'])[1]]
      : null;
?>
<div class="top"><div class="wrap row-between">
  <a class="brand" href="forum.php?v=list<?= fh($ticketQs) ?>">
    <span class="mark">R24</span>
    <span>讨论区<span class="sub"> · RootMyS24</span></span>
  </a>
  <span class="sp"></span>
  <?php if ($meChip): ?>
    <a class="chip" href="<?= fh($meChip['href']) ?>">
      <span class="avatar sm" style="--h:<?= (int)$meChip['hue'] ?>"><?= fh($meChip['label'] !== '' ? $meChip['initials'] : '?') ?></span>
      <?= fh($meChip['label']) ?>
    </a>
  <?php else: ?>
    <a class="chip" href="forum.php?v=login<?= fh($ticketQs) ?>">登录</a>
    <a class="chip" href="forum.php?v=register<?= fh($ticketQs) ?>">注册</a>
  <?php endif; ?>
</div></div>

<main class="wrap">
<?php if ($flash !== ''): ?><div class="flash ok">✅ <span><?= fh($flash) ?></span></div><?php endif; ?>
<?php if ($flashErr !== ''): ?><div class="flash err">⚠️ <span><?= fh($flashErr) ?></span></div><?php endif; ?>
<?php if ($installBanned !== null || $userBanned): ?>
  <div class="flash err">🚫 <span>当前<?= $userBanned ? '账号' : '设备标识' ?>已被限制发言<?= ($installBanned ?? '') !== '' ? '：' . fh((string)$installBanned) : '' ?>。浏览不受影响。</span></div>
<?php endif; ?>

<?php if ($thread): ?>
  <?php /* ────────── 主题详情 ────────── */ ?>
  <div class="crumbs">
    <a href="forum.php?v=list<?= fh($ticketQs) ?>">← 主题列表</a>
    <span>·</span>
    <a href="forum.php?v=list&amp;cat=<?= fh((string)$thread['topic']) ?><?= fh($ticketQs) ?>"><?= fh(forum_topic_label((string)$thread['topic'])) ?></a>
  </div>
  <div class="t-top">
    <span class="badge topic" style="--h:<?= (int)forum_topic_hue((string)$thread['topic']) ?>"><?= fh(forum_topic_label((string)$thread['topic'])) ?></span>
    <?php if ((int)$thread['pinned'] === 1): ?><span class="badge pin">置顶</span><?php endif; ?>
    <?php if ((int)$thread['locked'] === 1): ?><span class="badge">已锁定</span><?php endif; ?>
  </div>
  <h1 class="thread-title"><?= fh((string)$thread['title']) ?></h1>
  <div class="t-meta" style="margin-bottom:14px">
    <span><?= (int)$thread['posts'] ?> 楼</span>
    <span><?= (int)$thread['views'] ?> 浏览</span>
    <span>最后活动 <?= fh(forum_ago((string)$thread['updated_at'])) ?></span>
    <?php if ($user && (int)($thread['user_id'] ?? 0) === (int)$user['id']): ?>
      <span class="sp"></span>
      <form method="post" class="inline" data-confirm="删除该主题？此操作不可撤销。">
        <input type="hidden" name="action" value="del_own_thread">
        <input type="hidden" name="thread_id" value="<?= (int)$thread['id'] ?>">
        <input type="hidden" name="csrf" value="<?= fh($csrf) ?>">
        <input type="hidden" name="t" value="<?= fh($ticketRaw) ?>">
        <button class="btn sm danger" type="submit">删除我的主题</button>
      </form>
    <?php endif; ?>
  </div>

  <?php foreach ($posts as $i => $p): $floor = ($page - 1) * F_POSTS_PAGE + $i + 1;
        $pname = (string)($p['author'] ?? '') !== '' ? (string)$p['author'] : forum_user_label((string)$p['install_hash'], (string)$p['nickname']);
        [$phue, $pinit] = forum_avatar($pname);
  ?>
    <article class="post<?= $floor === 1 ? ' op' : '' ?>" id="p<?= (int)$p['id'] ?>">
      <div class="p-head">
        <?php if ((int)($p['user_id'] ?? 0) > 0): ?>
          <a href="forum.php?v=u&amp;id=<?= (int)$p['user_id'] ?><?= fh($ticketQs) ?>"><span class="avatar" style="--h:<?= (int)$phue ?>"><?= fh($pinit) ?></span></a>
        <?php else: ?>
          <span class="avatar" style="--h:<?= (int)$phue ?>"><?= fh($pinit) ?></span>
        <?php endif; ?>
        <span>
          <span class="p-who">
            <?php if ((int)($p['user_id'] ?? 0) > 0): ?>
              <a href="forum.php?v=u&amp;id=<?= (int)$p['user_id'] ?><?= fh($ticketQs) ?>"><?= fh($pname) ?></a>
            <?php else: ?>
              <?= fh($pname) ?>
            <?php endif; ?>
          </span>
          <?php if ($floor === 1): ?><span class="badge role">楼主</span><?php endif; ?>
          <br><span class="p-time"><?= $floor ?> 楼 · <?= fh(forum_ago((string)$p['created_at'])) ?></span>
        </span>
      </div>
      <div class="p-body"><?= forum_render_body((string)$p['body']) ?></div>
      <div class="p-actions">
        <?php if ($user && !$userBanned): ?>
          <form method="post">
            <input type="hidden" name="action" value="like">
            <input type="hidden" name="post_id" value="<?= (int)$p['id'] ?>">
            <input type="hidden" name="thread_id" value="<?= (int)$thread['id'] ?>">
            <input type="hidden" name="csrf" value="<?= fh($csrf) ?>">
            <input type="hidden" name="t" value="<?= fh($ticketRaw) ?>">
            <button class="like<?= isset($likedPosts[(int)$p['id']]) ? ' on' : '' ?>" type="submit">
              <?= isset($likedPosts[(int)$p['id']]) ? '♥' : '♡' ?> <?= (int)$p['likes'] ?>
            </button>
          </form>
        <?php else: ?>
          <span class="like" style="cursor:default">♡ <?= (int)$p['likes'] ?></span>
          <span class="hint">登录后可点赞</span>
        <?php endif; ?>
        <?php if ($user && (int)($p['user_id'] ?? 0) === (int)$user['id'] && $floor !== 1): ?>
          <span class="sp"></span>
          <form method="post" data-confirm="删除这条回复？">
            <input type="hidden" name="action" value="del_own_post">
            <input type="hidden" name="post_id" value="<?= (int)$p['id'] ?>">
            <input type="hidden" name="csrf" value="<?= fh($csrf) ?>">
            <input type="hidden" name="t" value="<?= fh($ticketRaw) ?>">
            <button class="btn sm danger" type="submit">删除</button>
          </form>
        <?php endif; ?>
      </div>
    </article>
  <?php endforeach; ?>
  <?php if (!$posts): ?><div class="empty"><strong>该主题的发言已被隐藏</strong></div><?php endif; ?>

  <?php if ($postPages > 1): ?>
    <div class="pager">
      <?php if ($page > 1): ?><a href="forum.php?v=t&amp;id=<?= (int)$thread['id'] ?>&amp;p=<?= $page - 1 ?><?= fh($ticketQs) ?>">← 上一页</a><?php endif; ?>
      <span>第 <?= $page ?> / <?= $postPages ?> 页</span>
      <?php if ($page < $postPages): ?><a href="forum.php?v=t&amp;id=<?= (int)$thread['id'] ?>&amp;p=<?= $page + 1 ?><?= fh($ticketQs) ?>">下一页 →</a><?php endif; ?>
    </div>
  <?php endif; ?>
  <a id="bottom"></a>

  <?php if ((int)$thread['locked'] === 1): ?>
    <div class="empty"><strong>该主题已锁定</strong>不再接受新回复。</div>
  <?php elseif (!$canPost): ?>
    <div class="empty"><strong>当前身份被限制发言</strong>无法回复该主题。</div>
  <?php else: ?>
    <form class="compose hi" method="post" action="forum.php" data-composer="t<?= (int)$thread['id'] ?>">
      <input type="hidden" name="action" value="reply">
      <input type="hidden" name="thread_id" value="<?= (int)$thread['id'] ?>">
      <input type="hidden" name="csrf" value="<?= fh($csrf) ?>">
      <input type="hidden" name="t" value="<?= fh($ticketRaw) ?>">
      <label class="f">回复<?= $user ? '（以 ' . fh((string)$user['username']) . ' 的身份）' : '（匿名）' ?></label>
      <textarea name="body" required minlength="2" maxlength="8000" placeholder="支持 Markdown 子集：```代码块```、`行内代码`、**粗体**、> 引用"></textarea>
      <div class="actions">
        <button class="btn" type="submit">发布回复</button>
        <span class="hint">Enter 换行 · 纯文本安全渲染</span>
      </div>
      <div class="fmt-help"><span><code>```</code> 代码块</span><span><code>`x`</code> 行内代码</span><span><code>**粗体**</code></span><span><code>&gt; 引用</code></span></div>
    </form>
  <?php endif; ?>

<?php elseif ($view === 'login' || $view === 'register'): ?>
  <?php /* ────────── 登录 / 注册 ────────── */ ?>
  <div class="card-sec" style="max-width:520px;margin:0 auto">
    <div class="tabs">
      <a class="<?= $view === 'login' ? 'on' : '' ?>" href="forum.php?v=login<?= fh($ticketQs) ?>">登录</a>
      <a class="<?= $view === 'register' ? 'on' : '' ?>" href="forum.php?v=register<?= fh($ticketQs) ?>">注册</a>
    </div>
    <?php if ($user): ?>
      <p class="hint">你已经以 <b><?= fh((string)$user['username']) ?></b> 的身份登录。<a href="forum.php?v=me<?= fh($ticketQs) ?>">进入我的主页</a></p>
    <?php elseif ($view === 'login'): ?>
      <h3>登录</h3>
      <form method="post" action="forum.php">
        <input type="hidden" name="action" value="login">
        <input type="hidden" name="t" value="<?= fh($ticketRaw) ?>">
        <div class="grid">
          <div><label class="f" for="lu">用户名</label><input type="text" id="lu" name="username" required maxlength="16" autocomplete="username"></div>
          <div><label class="f" for="lp">密码</label><input type="password" id="lp" name="password" required autocomplete="current-password"></div>
        </div>
        <div class="actions"><button class="btn" type="submit">登录</button>
        <span class="hint">还没有账号？<a href="forum.php?v=register<?= fh($ticketQs) ?>">注册一个</a></span></div>
      </form>
    <?php else: ?>
      <h3>注册</h3>
      <p class="hint" style="margin:0 0 12px">用户名 2–16 字（中文、字母、数字、下划线、连字符），密码 6 位以上。讨论区仅对持口令用户开放，注册同样需要口令。</p>
      <form method="post" action="forum.php">
        <input type="hidden" name="action" value="register">
        <input type="hidden" name="t" value="<?= fh($ticketRaw) ?>">
        <div class="grid">
          <div><label class="f" for="ru">用户名</label><input type="text" id="ru" name="username" required maxlength="16" autocomplete="username"></div>
          <div class="grid two">
            <div><label class="f" for="rp">密码</label><input type="password" id="rp" name="password" required autocomplete="new-password"></div>
            <div><label class="f" for="rp2">确认密码</label><input type="password" id="rp2" name="password2" required autocomplete="new-password"></div>
          </div>
        </div>
        <div class="actions"><button class="btn" type="submit">创建账号</button>
        <span class="hint">注册即表示遵守社区规范</span></div>
      </form>
    <?php endif; ?>
  </div>

<?php elseif ($profile): ?>
  <?php /* ────────── 个人主页 / 我的 ────────── */
        [$uhue, $uinit] = forum_avatar((string)$profile['username']);
        $isMe = $user && (int)$user['id'] === (int)$profile['id']; ?>
  <div class="profile">
    <span class="avatar lg" style="--h:<?= (int)$uhue ?>"><?= fh($uinit) ?></span>
    <div class="who">
      <div class="name">
        <?= fh((string)$profile['username']) ?>
        <?php if ((string)$profile['role'] === 'admin'): ?><span class="badge role">管理员</span><?php endif; ?>
        <?php if ((int)$profile['banned'] === 1): ?><span class="badge pin">已封禁</span><?php endif; ?>
      </div>
      <div class="bio"><?= (string)$profile['bio'] !== '' ? fh((string)$profile['bio']) : '<span class="hint">这个人很懒，还没写简介</span>' ?></div>
      <div class="stats">
        <div><b><?= (int)$profile['threads'] ?></b>主题</div>
        <div><b><?= (int)$profile['posts'] ?></b>发言</div>
        <div><b><?= (int)$profile['likes_got'] ?></b>获赞</div>
        <div><b><?= fh(date('Y-m-d', strtotime((string)$profile['created_at']) ?: time())) ?></b>加入</div>
      </div>
    </div>
    <?php if ($isMe): ?>
      <form method="post" action="forum.php">
        <input type="hidden" name="action" value="logout">
        <input type="hidden" name="csrf" value="<?= fh($csrf) ?>">
        <input type="hidden" name="t" value="<?= fh($ticketRaw) ?>">
        <button class="btn ghost sm" type="submit">退出登录</button>
      </form>
    <?php endif; ?>
  </div>

  <?php if ($isMe): ?>
    <div class="card-sec">
      <h3>资料设置</h3>
      <form method="post" action="forum.php">
        <input type="hidden" name="action" value="save_profile">
        <input type="hidden" name="csrf" value="<?= fh($csrf) ?>">
        <input type="hidden" name="t" value="<?= fh($ticketRaw) ?>">
        <label class="f" for="bio">个人简介（最多 140 字）</label>
        <input type="text" id="bio" name="bio" maxlength="140" value="<?= fh((string)$profile['bio']) ?>" placeholder="比如：S9280 国行 DZH3，正在折腾内核适配">
        <div class="actions"><button class="btn sm" type="submit">保存简介</button></div>
      </form>
    </div>
    <div class="card-sec">
      <h3>修改密码</h3>
      <form method="post" action="forum.php">
        <input type="hidden" name="action" value="save_password">
        <input type="hidden" name="csrf" value="<?= fh($csrf) ?>">
        <input type="hidden" name="t" value="<?= fh($ticketRaw) ?>">
        <div class="grid two">
          <div><label class="f" for="op">当前密码</label><input type="password" id="op" name="old_password" required autocomplete="current-password"></div>
          <div><label class="f" for="np">新密码</label><input type="password" id="np" name="new_password" required autocomplete="new-password"></div>
        </div>
        <div class="actions"><button class="btn sm" type="submit">更新密码</button>
        <span class="hint">更新后其它设备上的登录态会立即失效</span></div>
      </form>
    </div>
  <?php endif; ?>

  <div class="card-sec">
    <h3>最近主题</h3>
    <?php if ($profileThreads): ?>
      <ul class="mini">
        <?php foreach ($profileThreads as $t): ?>
          <li>
            <a href="forum.php?v=t&amp;id=<?= (int)$t['id'] ?><?= fh($ticketQs) ?>"><?= fh((string)$t['title']) ?></a>
            <div class="meta"><?= fh(forum_topic_label((string)$t['topic'])) ?> · <?= (int)$t['replies'] ?> 楼 · <?= fh(forum_ago((string)$t['updated_at'])) ?></div>
          </li>
        <?php endforeach; ?>
      </ul>
    <?php else: ?>
      <p class="hint">还没有发过主题。</p>
    <?php endif; ?>
  </div>

  <div class="card-sec">
    <h3>最近回复</h3>
    <?php if ($profilePosts): ?>
      <ul class="mini">
        <?php foreach ($profilePosts as $p): ?>
          <li>
            <a href="forum.php?v=t&amp;id=<?= (int)$p['thread_id'] ?>#p<?= (int)$p['id'] ?><?= fh($ticketQs) ?>"><?= fh((string)$p['thread_title']) ?></a>
            <div class="meta"><?= fh(forum_ago((string)$p['created_at'])) ?> · ♥ <?= (int)$p['likes'] ?></div>
            <div class="hint" style="margin-top:3px"><?= fh(mb_substr((string)$p['body'], 0, 90)) ?><?= mb_strlen((string)$p['body']) > 90 ? '…' : '' ?></div>
          </li>
        <?php endforeach; ?>
      </ul>
    <?php else: ?>
      <p class="hint">还没有回复过。</p>
    <?php endif; ?>
  </div>

<?php else: ?>
  <?php /* ────────── 主题列表 ────────── */ ?>
  <div class="hero">
    <h1>RootMyS24 讨论区</h1>
    <p>免解锁 root 的适配、排错与经验分享。发帖前建议写明：机型 / 固件 / 现象 / 已尝试的做法。</p>
    <div class="kpis">
      <div class="kpi"><b><?= (int)$counts['threads'] ?></b>主题</div>
      <div class="kpi"><b><?= (int)$counts['posts'] ?></b>发言</div>
      <div class="kpi"><b><?= (int)$counts['users'] ?></b>用户</div>
      <?php if ($user): ?>
        <div class="kpi"><b><?= fh((string)$user['username']) ?></b>已登录</div>
      <?php else: ?>
        <div class="kpi"><b><a href="forum.php?v=register<?= fh($ticketQs) ?>">注册</a></b>获得署名与点赞</div>
      <?php endif; ?>
    </div>
  </div>

  <div class="toolbar">
    <div class="tools">
      <a class="<?= $cat === '' ? 'on' : '' ?>" href="forum.php?v=list&amp;sort=<?= fh($sort) ?><?= fh($ticketQs) ?>">全部<span class="n"><?= array_sum($catCounts) ?></span></a>
      <?php foreach ($topics as $k => $label): if (($catCounts[$k] ?? 0) === 0 && $cat !== $k) { continue; } ?>
        <a class="<?= $cat === $k ? 'on' : '' ?>" href="forum.php?v=list&amp;cat=<?= fh($k) ?>&amp;sort=<?= fh($sort) ?><?= fh($ticketQs) ?>"><?= fh($label) ?><span class="n"><?= (int)($catCounts[$k] ?? 0) ?></span></a>
      <?php endforeach; ?>
    </div>
  </div>
  <div class="toolbar">
    <form class="search" method="get" action="forum.php">
      <input type="hidden" name="v" value="list">
      <?php if ($ticketRaw !== ''): ?><input type="hidden" name="t" value="<?= fh($ticketRaw) ?>"><?php endif; ?>
      <?php if ($cat !== ''): ?><input type="hidden" name="cat" value="<?= fh($cat) ?>"><?php endif; ?>
      <input type="search" name="q" value="<?= fh($q) ?>" placeholder="搜索标题或正文…" aria-label="搜索">
      <button class="btn ghost sm" type="submit">搜索</button>
    </form>
    <select data-nav aria-label="排序">
      <?php foreach (['active' => '最新回复', 'new' => '最新发布', 'hot' => '最热'] as $k => $label):
        $url = 'forum.php?v=list&sort=' . $k . ($cat !== '' ? '&cat=' . rawurlencode($cat) : '') . ($q !== '' ? '&q=' . rawurlencode($q) : '') . $ticketQs; ?>
        <option value="<?= fh($url) ?>" <?= $sort === $k ? 'selected' : '' ?>><?= fh($label) ?></option>
      <?php endforeach; ?>
    </select>
  </div>
  <?php if ($q !== ''): ?>
    <p class="hint" style="margin:-4px 0 12px">「<?= fh($q) ?>」的搜索结果：<?= (int)$totalRows ?> 条 · <a href="forum.php?v=list<?= fh($ticketQs) ?>">清除</a></p>
  <?php endif; ?>

  <?php if ($canPost): ?>
    <details class="new" id="new"<?= $counts['threads'] === 0 ? ' open' : '' ?>>
      <summary>✏️ 发新主题</summary>
      <form class="compose" method="post" action="forum.php" data-composer="new">
        <input type="hidden" name="action" value="new_thread">
        <input type="hidden" name="csrf" value="<?= fh($csrf) ?>">
        <input type="hidden" name="t" value="<?= fh($ticketRaw) ?>">
        <div class="grid two">
          <div><label class="f" for="title">标题</label>
            <input type="text" id="title" name="title" required minlength="4" maxlength="80" placeholder="一句话说清问题或分享"></div>
          <div><label class="f" for="topic">分类</label>
            <select id="topic" name="topic">
              <?php foreach ($topics as $k => $label): ?>
                <option value="<?= fh($k) ?>"<?= $k === 'discuss' ? ' selected' : '' ?>><?= fh($label) ?></option>
              <?php endforeach; ?>
            </select></div>
        </div>
        <div style="margin-top:10px">
          <label class="f" for="body">正文<?= $user ? '（以 ' . fh((string)$user['username']) . ' 的身份发布）' : '（匿名发布）' ?></label>
          <textarea id="body" name="body" required minlength="4" maxlength="8000"
            placeholder="机型 / 固件版本 / 现象 / 已尝试的做法 —— 写清楚更容易被解答&#10;&#10;支持 ```代码块```、`行内代码`、**粗体**、> 引用"></textarea>
        </div>
        <div class="actions"><button class="btn" type="submit">发布主题</button>
          <?php if (!$user): ?><span class="hint"><a href="forum.php?v=login<?= fh($ticketQs) ?>">登录</a>后署名并可用点赞</span><?php endif; ?>
        </div>
      </form>
    </details>
  <?php endif; ?>

  <?php if (!$threads): ?>
    <div class="empty">
      <strong><?= ($q !== '' || $cat !== '') ? '没有匹配的主题' : '还没有人发帖' ?></strong>
      <?= ($q !== '' || $cat !== '') ? '换个关键词或分类试试。' : '讨论区刚开张，欢迎提第一个问题或分享你的适配经验。' ?>
    </div>
  <?php else: ?>
    <ul class="threads">
      <?php foreach ($threads as $t):
        $tname = (string)($t['author'] ?? '') !== '' ? (string)$t['author'] : forum_user_label((string)$t['install_hash'], (string)$t['nickname']);
        [$thue, $tinit] = forum_avatar($tname); ?>
        <li class="<?= (int)$t['pinned'] === 1 ? 'pin' : '' ?>">
          <?php if ((int)($t['user_id'] ?? 0) > 0): ?>
            <a href="forum.php?v=u&amp;id=<?= (int)$t['user_id'] ?><?= fh($ticketQs) ?>"><span class="avatar" style="--h:<?= (int)$thue ?>"><?= fh($tinit) ?></span></a>
          <?php else: ?>
            <span class="avatar" style="--h:<?= (int)$thue ?>"><?= fh($tinit) ?></span>
          <?php endif; ?>
          <div class="t-main">
            <div class="t-top">
              <span class="badge topic" style="--h:<?= (int)forum_topic_hue((string)$t['topic']) ?>"><?= fh(forum_topic_label((string)$t['topic'])) ?></span>
              <?php if ((int)$t['pinned'] === 1): ?><span class="badge pin">置顶</span><?php endif; ?>
              <?php if ((int)$t['locked'] === 1): ?><span class="badge">已锁定</span><?php endif; ?>
            </div>
            <a class="t-title" href="forum.php?v=t&amp;id=<?= (int)$t['id'] ?><?= fh($ticketQs) ?>"><?= fh((string)$t['title']) ?></a>
            <div class="t-meta">
              <span><?php if ((int)($t['user_id'] ?? 0) > 0): ?><a href="forum.php?v=u&amp;id=<?= (int)$t['user_id'] ?><?= fh($ticketQs) ?>"><?= fh($tname) ?></a><?php else: ?><?= fh($tname) ?><?php endif; ?></span>
              <span><?= fh(forum_ago((string)$t['updated_at'])) ?></span>
              <span>♥ <?= (int)$t['likes'] ?></span>
            </div>
          </div>
          <div class="t-stats">
            <span class="num"><?= (int)$t['replies'] ?></span><span class="lbl">回复</span>
            <span class="num"><?= (int)$t['views'] ?></span><span class="lbl">浏览</span>
          </div>
        </li>
      <?php endforeach; ?>
    </ul>
    <?php if ($pages > 1): ?>
      <div class="pager">
        <?php
          $mk = static fn(int $p): string => 'forum.php?v=list&p=' . $p
              . ($cat !== '' ? '&cat=' . rawurlencode($cat) : '')
              . ($q !== '' ? '&q=' . rawurlencode($q) : '')
              . ($sort !== 'active' ? '&sort=' . $sort : '') . $ticketQs;
        ?>
        <?php if ($page > 1): ?><a href="<?= fh($mk($page - 1)) ?>">← 上一页</a><?php endif; ?>
        <span>第 <?= $page ?> / <?= $pages ?> 页 · 共 <?= (int)$totalRows ?> 个主题</span>
        <?php if ($page < $pages): ?><a href="<?= fh($mk($page + 1)) ?>">下一页 →</a><?php endif; ?>
      </div>
    <?php endif; ?>
  <?php endif; ?>
<?php endif; ?>

  <footer>
    <p style="margin:0">
      RootMyS24 讨论区 · 身份以账号为准，未登录则以匿名代号标记（不可反查）。<br>
      请勿发布隐私、账号、支付或他人设备信息；违规内容会被隐藏，账号可能被封禁。
    </p>
  </footer>
</main>

<button class="toplink" id="toTop" type="button" aria-label="回到顶部">↑</button>
<script nonce="<?= fh($nonce) ?>">
(function () {
  // 输入框随内容长高
  document.querySelectorAll('textarea').forEach(function (ta) {
    var fit = function () { ta.style.height = 'auto'; ta.style.height = Math.min(ta.scrollHeight + 4, 520) + 'px'; };
    ta.addEventListener('input', fit); fit();
  });
  // 代码块复制
  document.querySelectorAll('button[data-copy]').forEach(function (b) {
    b.addEventListener('click', function () {
      var pre = b.closest('.codeblock').querySelector('pre');
      var txt = pre ? pre.innerText : '';
      var done = function () { b.textContent = '已复制'; setTimeout(function () { b.textContent = '复制'; }, 1400); };
      if (navigator.clipboard) { navigator.clipboard.writeText(txt).then(done, done); }
      else {
        var r = document.createRange(); r.selectNodeContents(pre);
        var s = getSelection(); s.removeAllRanges(); s.addRange(r);
        try { document.execCommand('copy'); } catch (e) {}
        done();
      }
    });
  });
  // 草稿：按 composer 维度存本地，误刷新不丢内容
  document.querySelectorAll('form[data-composer]').forEach(function (f) {
    var key = 'rms24.draft.' + f.dataset.composer;
    var ta = f.querySelector('textarea'); if (!ta) return;
    try { var d = localStorage.getItem(key); if (d && !ta.value) ta.value = d; } catch (e) {}
    ta.addEventListener('input', function () {
      try { ta.value.trim() ? localStorage.setItem(key, ta.value) : localStorage.removeItem(key); } catch (e) {}
    });
    f.addEventListener('submit', function () { try { localStorage.removeItem(key); } catch (e) {} });
  });
  // 下拉即跳转（避免内联事件被 CSP 拦下）
  document.querySelectorAll('[data-nav]').forEach(function (el) {
    el.addEventListener('change', function () { if (el.value) location.href = el.value; });
  });
  // 危险操作二次确认
  document.querySelectorAll('form[data-confirm]').forEach(function (f) {
    f.addEventListener('submit', function (e) {
      if (!confirm(f.dataset.confirm)) e.preventDefault();
    });
  });
  // 回到顶部
  var btn = document.getElementById('toTop');
  if (btn) {
    addEventListener('scroll', function () { btn.classList.toggle('show', scrollY > 600); }, { passive: true });
    btn.addEventListener('click', function () { scrollTo({ top: 0, behavior: 'smooth' }); });
  }
})();
</script>
</body></html>
