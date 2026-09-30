<?php
/**
 * RootMyS24 讨论区 —— 数据层：连接、建表/迁移、公共助手
 *
 * 本文件会随仓库开源，因此**不含任何凭据**。数据库账号按以下顺序解析：
 *   1. db_secret.php            （推荐：服务器上单独放，不进仓库）
 *   2. forum_secret.php['db']   （与令牌闸门共用同一个秘密文件）
 *   3. 环境变量 RMS24_DB_DSN / RMS24_DB_USER / RMS24_DB_PASS
 *
 * 表结构用 CREATE TABLE IF NOT EXISTS + 幂等 ALTER 描述，首次访问自动升级，
 * 部署 = 上传文件。
 */
declare(strict_types=1);

// ─────────────────────────────────────────────────────────────
// 连接与建表
// ─────────────────────────────────────────────────────────────

function rms24_db(): PDO
{
    static $pdo = null;
    if ($pdo instanceof PDO) {
        return $pdo;
    }

    $dsn = '';
    $user = '';
    $pass = '';

    $dbSecret = __DIR__ . '/db_secret.php';
    $secretFile = __DIR__ . '/forum_secret.php';

    if (is_file($dbSecret)) {
        $cfg = (array)require $dbSecret;
        $dsn = (string)($cfg['dsn'] ?? '');
        $user = (string)($cfg['user'] ?? '');
        $pass = (string)($cfg['pass'] ?? '');
    } elseif (is_file($secretFile)) {
        $cfg = (array)require $secretFile;
        $db = (array)($cfg['db'] ?? []);
        $dsn = (string)($db['dsn'] ?? '');
        $user = (string)($db['user'] ?? '');
        $pass = (string)($db['pass'] ?? '');
    }
    if ($dsn === '') {
        $dsn = (string)(getenv('RMS24_DB_DSN') ?: '');
        $user = (string)(getenv('RMS24_DB_USER') ?: '');
        $pass = (string)(getenv('RMS24_DB_PASS') ?: '');
    }
    if ($dsn === '') {
        $dsn = 'mysql:host=127.0.0.1;port=3306;dbname=rootmys24;charset=utf8mb4';
    }
    if ($user === '') {
        $user = 'rootmys24';
    }

    $pdo = new PDO($dsn, $user, $pass, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);
    return $pdo;
}

/** 列是否存在（用于幂等迁移）。 */
function rms24_has_column(PDO $pdo, string $table, string $column): bool
{
    $st = $pdo->prepare(
        'SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?'
    );
    $st->execute([$table, $column]);
    return (int)$st->fetchColumn() > 0;
}

/** 建表 + 迁移（幂等）。 */
function rms24_schema(PDO $pdo): bool
{
    static $done = false;
    if ($done) {
        return true;
    }

    $pdo->exec(<<<SQL
CREATE TABLE IF NOT EXISTS forum_users (
  id            INT UNSIGNED NOT NULL AUTO_INCREMENT,
  username      VARCHAR(16)  NOT NULL,
  pass_hash     VARCHAR(255) NOT NULL,
  role          VARCHAR(12)  NOT NULL DEFAULT 'user',
  banned        TINYINT(1)   NOT NULL DEFAULT 0,
  ban_reason    VARCHAR(120) NOT NULL DEFAULT '',
  bio           VARCHAR(140) NOT NULL DEFAULT '',
  token_version INT UNSIGNED NOT NULL DEFAULT 1,
  threads       INT UNSIGNED NOT NULL DEFAULT 0,
  posts         INT UNSIGNED NOT NULL DEFAULT 0,
  likes_got     INT UNSIGNED NOT NULL DEFAULT 0,
  created_at    DATETIME     NOT NULL,
  last_seen     DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
SQL);

    $pdo->exec(<<<SQL
CREATE TABLE IF NOT EXISTS forum_threads (
  id            INT UNSIGNED NOT NULL AUTO_INCREMENT,
  topic         VARCHAR(16)  NOT NULL DEFAULT 'discuss',
  title         VARCHAR(120) NOT NULL,
  install_hash  CHAR(12)     NOT NULL DEFAULT '',
  user_id       INT UNSIGNED NULL DEFAULT NULL,
  nickname      VARCHAR(24)  NOT NULL DEFAULT '',
  posts         INT UNSIGNED NOT NULL DEFAULT 1,
  views         INT UNSIGNED NOT NULL DEFAULT 0,
  likes         INT UNSIGNED NOT NULL DEFAULT 0,
  pinned        TINYINT(1)   NOT NULL DEFAULT 0,
  locked        TINYINT(1)   NOT NULL DEFAULT 0,
  hidden        TINYINT(1)   NOT NULL DEFAULT 0,
  created_at    DATETIME     NOT NULL,
  updated_at    DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_list (hidden, pinned, updated_at),
  KEY idx_install (install_hash),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
SQL);

    $pdo->exec(<<<SQL
CREATE TABLE IF NOT EXISTS forum_posts (
  id            INT UNSIGNED NOT NULL AUTO_INCREMENT,
  thread_id     INT UNSIGNED NOT NULL,
  install_hash  CHAR(12)     NOT NULL DEFAULT '',
  user_id       INT UNSIGNED NULL DEFAULT NULL,
  nickname      VARCHAR(24)  NOT NULL DEFAULT '',
  body          MEDIUMTEXT   NOT NULL,
  likes         INT UNSIGNED NOT NULL DEFAULT 0,
  hidden        TINYINT(1)   NOT NULL DEFAULT 0,
  ip_hash       CHAR(16)     NOT NULL DEFAULT '',
  created_at    DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_thread (thread_id, hidden, id),
  KEY idx_install (install_hash),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
SQL);

    $pdo->exec(<<<SQL
CREATE TABLE IF NOT EXISTS forum_likes (
  post_id    INT UNSIGNED NOT NULL,
  user_id    INT UNSIGNED NOT NULL,
  created_at DATETIME     NOT NULL,
  PRIMARY KEY (post_id, user_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
SQL);

    $pdo->exec(<<<SQL
CREATE TABLE IF NOT EXISTS forum_bans (
  id            INT UNSIGNED NOT NULL AUTO_INCREMENT,
  install_hash  CHAR(12)     NOT NULL DEFAULT '',
  reason        VARCHAR(120) NOT NULL DEFAULT '',
  created_at    DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_install (install_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
SQL);

    $pdo->exec(<<<SQL
CREATE TABLE IF NOT EXISTS forum_rate (
  bucket     VARCHAR(24) NOT NULL,
  rkey       VARCHAR(64) NOT NULL,
  created_at INT UNSIGNED NOT NULL,
  KEY idx_lookup (bucket, rkey, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
SQL);

    // ── 从"匿名版"升级到"用户系统"：老表补列（幂等） ──────────────
    $alters = [
        ['forum_threads', 'user_id', 'ALTER TABLE forum_threads ADD COLUMN user_id INT UNSIGNED NULL DEFAULT NULL AFTER install_hash'],
        ['forum_threads', 'likes',   'ALTER TABLE forum_threads ADD COLUMN likes INT UNSIGNED NOT NULL DEFAULT 0 AFTER views'],
        ['forum_posts',   'user_id', 'ALTER TABLE forum_posts ADD COLUMN user_id INT UNSIGNED NULL DEFAULT NULL AFTER install_hash'],
        ['forum_posts',   'likes',   'ALTER TABLE forum_posts ADD COLUMN likes INT UNSIGNED NOT NULL DEFAULT 0 AFTER body'],
    ];
    foreach ($alters as [$table, $column, $sql]) {
        if (!rms24_has_column($pdo, $table, $column)) {
            $pdo->exec($sql);
            $pdo->exec("ALTER TABLE {$table} ADD KEY idx_{$column} ({$column})");
        }
    }

    $done = true;
    return true;
}

// ─────────────────────────────────────────────────────────────
// 基础助手
// ─────────────────────────────────────────────────────────────

function rms24_now(): string
{
    return date('Y-m-d H:i:s');
}

function fh(?string $s): string
{
    return htmlspecialchars((string)$s, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

/** 输入清洗：去掉控制字符、统一换行、压缩空行、限长。 */
function forum_clean_text(string $s, int $max): string
{
    $s = str_replace(["\r\n", "\r"], "\n", $s);
    $s = (string)preg_replace('/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/u', '', $s);
    $s = (string)preg_replace("/\n{4,}/", "\n\n\n", $s);
    $s = trim($s);
    return mb_substr($s, 0, $max);
}

/** 单行清洗（标题、昵称等）。 */
function forum_clean_line(string $s, int $max): string
{
    $s = trim((string)preg_replace('/\s+/u', ' ', strip_tags(str_replace(["\n", "\r"], ' ', $s))));
    return mb_substr($s, 0, $max);
}

/** 相对时间。 */
function forum_ago(string $datetime): string
{
    $ts = strtotime($datetime);
    if (!$ts) {
        return $datetime;
    }
    $d = time() - $ts;
    if ($d < 60) {
        return '刚刚';
    }
    if ($d < 3600) {
        return intdiv($d, 60) . ' 分钟前';
    }
    if ($d < 86400) {
        return intdiv($d, 3600) . ' 小时前';
    }
    if ($d < 86400 * 30) {
        return intdiv($d, 86400) . ' 天前';
    }
    return date('Y-m-d', $ts);
}

/** 匿名代号展示名（未登录发言时使用）。 */
function forum_user_label(string $installHash, string $nickname = ''): string
{
    $nickname = trim($nickname);
    $uid = substr($installHash, 0, 8);
    if ($nickname !== '') {
        return $nickname;
    }
    return '匿名 ' . ($uid !== '' ? $uid : '访客');
}

// ─────────────────────────────────────────────────────────────
// 用户系统
// ─────────────────────────────────────────────────────────────

/** 用户名规则：2–16 字，中文/字母/数字/下划线/连字符，不能纯数字。 */
function forum_username_error(string $name): string
{
    $len = mb_strlen($name);
    if ($len < 2 || $len > 16) {
        return '用户名需要 2–16 个字';
    }
    if (!preg_match('/^[\p{Han}A-Za-z0-9_-]+$/u', $name)) {
        return '用户名只能用中文、字母、数字、下划线或连字符';
    }
    if (preg_match('/^\d+$/', $name)) {
        return '用户名不能是纯数字';
    }
    // 保留字：只挡明显的官方冒名，站长本人的 NanoTurtle 不在此列
    if (preg_match('/^(admin|administrator|root|管理员|官方|system|moderator)$/iu', $name)) {
        return '该用户名为保留字';
    }
    return '';
}

/** 头像：由用户名派生色相（不依赖任何外部资源）。 */
function forum_avatar(string $name): array
{
    $h = (int)hexdec(substr(md5($name !== '' ? $name : 'anon'), 0, 6));
    $hue = $h % 360;
    $initials = mb_strtoupper(mb_substr($name !== '' ? $name : '?', 0, 2));
    return [$hue, $initials];
}

/** 由 id 取用户。 */
function forum_user(PDO $pdo, int $id): ?array
{
    if ($id <= 0) {
        return null;
    }
    $st = $pdo->prepare('SELECT * FROM forum_users WHERE id = ? LIMIT 1');
    $st->execute([$id]);
    return $st->fetch() ?: null;
}

function forum_user_by_name(PDO $pdo, string $name): ?array
{
    $st = $pdo->prepare('SELECT * FROM forum_users WHERE username = ? LIMIT 1');
    $st->execute([$name]);
    return $st->fetch() ?: null;
}

// ─────────────────────────────────────────────────────────────
// 分类与限速
// ─────────────────────────────────────────────────────────────

function forum_topics(): array
{
    return [
        'discuss' => '讨论',
        'help'    => '求助',
        'share'   => '分享',
        'guide'   => '教程',
        'bug'     => '反馈',
        'other'   => '其他',
    ];
}

function forum_topic_label(string $topic): string
{
    $all = forum_topics();
    return $all[$topic] ?? $all['other'];
}

/** 分类色相：让列表一眼能分出板块。 */
function forum_topic_hue(string $topic): int
{
    return [
        'discuss' => 214,
        'help'    => 12,
        'share'   => 152,
        'guide'   => 268,
        'bug'     => 38,
        'other'   => 210,
    ][$topic] ?? 210;
}

/** 数据库限速：true = 应拒绝。 */
function forum_rate_limit(PDO $pdo, string $bucket, string $key, int $limit, int $window): bool
{
    $now = time();
    if (random_int(1, 40) === 1) {
        $pdo->prepare('DELETE FROM forum_rate WHERE created_at < ?')->execute([$now - 7200]);
    }
    $st = $pdo->prepare('SELECT COUNT(*) FROM forum_rate WHERE bucket = ? AND rkey = ? AND created_at > ?');
    $st->execute([$bucket, $key, $now - $window]);
    if ((int)$st->fetchColumn() >= $limit) {
        return true;
    }
    $pdo->prepare('INSERT INTO forum_rate (bucket, rkey, created_at) VALUES (?, ?, ?)')
        ->execute([$bucket, $key, $now]);
    return false;
}

/** 距上次发帖的间隔（秒）。 */
function forum_too_fast(PDO $pdo, string $installHash, int $seconds, int $userId = 0): bool
{
    if ($userId > 0) {
        $st = $pdo->prepare('SELECT MAX(created_at) FROM forum_posts WHERE user_id = ?');
        $st->execute([$userId]);
    } else {
        $st = $pdo->prepare('SELECT MAX(created_at) FROM forum_posts WHERE install_hash = ?');
        $st->execute([$installHash]);
    }
    $last = (string)($st->fetchColumn() ?: '');
    if ($last === '') {
        return false;
    }
    $ts = strtotime($last);
    return $ts !== false && (time() - $ts) < $seconds;
}

// ─────────────────────────────────────────────────────────────
// 正文渲染：先转义再套用极小 Markdown 子集
//   围栏代码块、`行内代码`、**粗体**、*斜体*、> 引用、链接、换行
//   任何情况下都不允许原始 HTML 通过。
// ─────────────────────────────────────────────────────────────

function forum_render_body(string $body): string
{
    $t = str_replace(["\r\n", "\r"], "\n", $body);
    $t = (string)preg_replace('/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/u', '', $t);

    // 1) 先摘出围栏代码块（其内容单独转义，不再套用其它规则）
    $blocks = [];
    $t = (string)preg_replace_callback(
        '/```([A-Za-z0-9_+.-]*)\n(.*?)```/s',
        static function (array $m) use (&$blocks): string {
            $code = fh(rtrim($m[2], "\n"));
            $lang = $m[1] !== '' ? '<span class="code-lang">' . fh($m[1]) . '</span>' : '<span class="code-lang">code</span>';
            $blocks[] = '<div class="codeblock"><div class="code-head">' . $lang
                . '<button class="copy" type="button" data-copy>复制</button></div><pre><code>' . $code . '</code></pre></div>';
            return "\x02B" . (count($blocks) - 1) . "\x02";
        },
        $t
    );

    // 2) 整体转义后套用轻量标记
    $t = fh($t);
    $t = (string)preg_replace('/^&gt;\s?(.*)$/m', '<blockquote>$1</blockquote>', $t);
    $t = (string)preg_replace('/`([^`\n]+)`/u', '<code>$1</code>', $t);
    $t = (string)preg_replace('/\*\*([^*\n]+)\*\*/u', '<strong>$1</strong>', $t);
    $t = (string)preg_replace('/(?<![\w*])\*([^*\n]+)\*(?!\*)/u', '<em>$1</em>', $t);
    $t = (string)preg_replace_callback(
        '~https?://[^\s<>"\']+~u',
        static fn(array $m): string => '<a href="' . $m[0] . '" rel="nofollow noopener noreferrer" target="_blank">' . $m[0] . '</a>',
        $t
    );
    $t = nl2br($t, false);

    // 3) 还原代码块
    return (string)preg_replace_callback(
        '/\x02B(\d+)\x02/',
        static fn(array $m): string => $blocks[(int)$m[1]] ?? '',
        $t
    );
}
