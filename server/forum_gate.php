<?php
/**
 * RootMyS24 讨论区 —— 令牌闸门（客户端唯一需要调用的接口）
 *
 * ## 为什么秘密放在这里，而不是 App 里
 *
 * App 是开源的。**只要令牌（或它的可离线校验物）进了 APK，就等于公开** —— 反编译、
 * 静态扫描、动态抓包，总能拿到，而且一旦泄露只能靠发新版补救。
 * 所以设计成：
 *
 *   客户端：只有输入框 → 把口令发到这里校验 → 拿到一张短期票据 → 用它打开论坛页面
 *   服务端：只存口令的 HMAC（不存明文）；校验通过才签发票据；改口令不用发版
 *
 * 客户端不做任何离线校验（那会把可爆破的校验物塞进 APK），票据是 HMAC 签名 + 带过期时间，
 * 论坛页自己验签，客户端伪造不了。
 *
 * ## 部署
 *
 * 1. 生成 forum_secret.php（见文件末尾注释里的生成命令），填进 token_hash 与 hmac_key；
 * 2. 把本文件与 forum.php 一起放到 rms24_api/；
 * 3. App 侧「关于 → 讨论区」输入口令即可。
 *
 * ## 接口
 *
 *   POST /rms24_api/forum_gate.php
 *     token   口令（必填）
 *     install 安装标识（可选，用于限速与审计）
 *   成功 → {"ok":true,"url":"https://…/forum.php?t=<票据>","expires":<秒级时间戳>}
 *   失败 → {"ok":false,"error":"bad_token" | "rate_limited" | "no_secret"}
 */
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

$secretFile = __DIR__ . '/forum_secret.php';
if (!is_file($secretFile)) {
    http_response_code(503);
    echo json_encode(['ok' => false, 'error' => 'no_secret']);
    exit;
}
$secret = require $secretFile;
$tokenHash = (string)($secret['token_hash'] ?? '');
$hmacKey = (string)($secret['hmac_key'] ?? '');
if ($tokenHash === '' || $hmacKey === '') {
    http_response_code(503);
    echo json_encode(['ok' => false, 'error' => 'no_secret']);
    exit;
}

$ticketTtl = (int)($secret['ticket_ttl'] ?? 1800);   // 票据有效期（秒），默认 30 分钟

/** 限速：同一来源 + 安装标识，10 分钟内最多 20 次尝试（防在线爆破）。 */
function forum_rate_limited(string $key): bool {
    $dir = sys_get_temp_dir() . '/rms24_forum_rl';
    if (!is_dir($dir)) {
        @mkdir($dir, 0700, true);
    }
    $file = $dir . '/' . hash('sha256', $key) . '.txt';
    $now = time();
    $window = 600;
    $limit = 20;
    $hits = [];
    if (is_file($file)) {
        $hits = array_filter(
            array_map('intval', explode(',', (string)file_get_contents($file))),
            static fn(int $t): bool => $t > $now - $window
        );
    }
    if (count($hits) >= $limit) {
        return true;
    }
    $hits[] = $now;
    @file_put_contents($file, implode(',', $hits), LOCK_EX);
    return false;
}

/** 票据 = base64(payload) . '.' . HMAC(payload) */
function forum_ticket(string $key, int $ttl, string $install): array {
    $expires = time() + $ttl;
    $payload = json_encode(['exp' => $expires, 'i' => substr(hash('sha256', $install), 0, 12)]);
    $sig = hash_hmac('sha256', $payload, $key);
    return [rtrim(strtr(base64_encode($payload), '+/', '-_'), '=') . '.' . $sig, $expires];
}

$token = trim((string)($_POST['token'] ?? ''));
$install = trim((string)($_POST['install'] ?? ''));
if ($token === '') {
    echo json_encode(['ok' => false, 'error' => 'bad_token']);
    exit;
}

$ip = (string)($_SERVER['REMOTE_ADDR'] ?? '');
if (forum_rate_limited($ip . '|' . $install)) {
    http_response_code(429);
    echo json_encode(['ok' => false, 'error' => 'rate_limited']);
    exit;
}

// 恒定时间比较，避免计时侧信道（虽然在线爆破的门槛主要在限速上）
if (!hash_equals($tokenHash, hash_hmac('sha256', $token, $hmacKey))) {
    echo json_encode(['ok' => false, 'error' => 'bad_token']);
    exit;
}

[$ticket, $expires] = forum_ticket($hmacKey, $ticketTtl, $install);
$scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$host = $_SERVER['HTTP_HOST'] ?? 'blog.nanoturtle.cn';

echo json_encode([
    'ok' => true,
    'url' => $scheme . '://' . $host . '/rms24_api/forum.php?t=' . rawurlencode($ticket),
    'expires' => $expires,
], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);

/*
 * 生成 forum_secret.php（在服务器上执行一次，把口令换成你自己的）：
 *
 *   php -r '$t="你的口令"; $k=bin2hex(random_bytes(32));
 *     file_put_contents("forum_secret.php", "<?php\nreturn [\n  \"token_hash\" => \"".hash_hmac("sha256",$t,$k)."\",\n  \"hmac_key\" => \"$k\",\n  \"ticket_ttl\" => 1800,\n];\n");
 *     echo "ok\n";'
 */
