<?php
/**
 * RootMyS24 讨论区页面（网页版）——需要 forum_gate.php 签发的票据才能进入
 *
 * 票据 = base64(payload).HMAC(payload)，payload 里带过期时间。
 * 这里自己验签，所以客户端伪造不了、也不需要 cookie/session。
 *
 * 说明：本页目前是**壳**——真正的论坛内容可以换成 bbPress / wpForo 的页面，
 * 或者直接在这里往下写（帖子表 + 表单）。鉴权这一层已经做好，换引擎不用动 App。
 */
declare(strict_types=1);

$secretFile = __DIR__ . '/forum_secret.php';
$ok = false;
$reason = 'no_secret';

if (is_file($secretFile)) {
    $secret = require $secretFile;
    $hmacKey = (string)($secret['hmac_key'] ?? '');
    $ticket = (string)($_GET['t'] ?? '');
    if ($hmacKey !== '' && $ticket !== '') {
        $parts = explode('.', $ticket, 2);
        if (count($parts) === 2) {
            $payload = base64_decode(strtr($parts[0], '-_', '+/') . str_repeat('=', (4 - strlen($parts[0]) % 4) % 4), true);
            if ($payload !== false && hash_equals(hash_hmac('sha256', $payload, $hmacKey), $parts[1])) {
                $data = json_decode($payload, true);
                $exp = (int)($data['exp'] ?? 0);
                if ($exp > time()) {
                    $ok = true;
                } else {
                    $reason = 'expired';
                }
            } else {
                $reason = 'bad_ticket';
            }
        } else {
            $reason = 'bad_ticket';
        }
    } else {
        $reason = 'no_ticket';
    }
}

header('Content-Type: text/html; charset=utf-8');
header('Cache-Control: no-store');
?><!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>RootMyS24 讨论区</title>
<style>
  :root { color-scheme: light dark; }
  body { font-family: system-ui, -apple-system, "Noto Sans CJK SC", sans-serif;
         margin: 0; padding: 20px 16px 48px; line-height: 1.7; }
  h1 { font-size: 1.25rem; margin: 8px 0 4px; }
  .hint { color: #6b7280; font-size: .875rem; }
  .card { border: 1px solid #e5e7eb; border-radius: 14px; padding: 14px 16px; margin-top: 14px; }
  .deny { border-color: #fecaca; background: #fef2f2; color: #991b1b; }
  code { background: #f3f4f6; padding: 1px 5px; border-radius: 5px; }
</style>
</head>
<body>
<?php if ($ok): ?>
  <h1>讨论区</h1>
  <p class="hint">已验证口令，这里是 RootMyS24 的使用者讨论区。</p>
  <div class="card">
    <strong>论坛内容尚未接入</strong>
    <p class="hint">
      鉴权层已经就绪：令牌在服务端校验、票据短期有效、客户端不留任何秘密。<br>
      把真正的版块挂到这一页（bbPress / wpForo，或在本文件里继续写）即可，
      App 侧不需要重新发版。
    </p>
  </div>
<?php else: ?>
  <div class="card deny">
    <strong>无法进入讨论区</strong>
    <p class="hint">
      <?php
        echo match ($reason) {
            'expired' => '票据已过期，请在 App 里重新输入口令。',
            'no_ticket' => '缺少票据，请从 App 的「关于 → 讨论区」进入。',
            'no_secret' => '服务端尚未配置 forum_secret.php。',
            default => '票据无效，请从 App 重新进入。',
        };
      ?>
    </p>
  </div>
<?php endif; ?>
</body>
</html>
