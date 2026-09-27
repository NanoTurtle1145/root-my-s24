<?php
/**
 * RootMyS24 公告（announcer）——公开接口，无需令牌
 *
 * 数据源：同目录 announcements.json（人工编辑，或以后挂进 admin.php 的后台界面）。
 * App 拉取后渲染成卡片，只放行两种动作：
 *   - link   : 必须是 http/https，客户端用系统浏览器打开
 *   - action : 白名单内的应用内动作（check_update / open_settings / copy_text / dismiss）
 *
 * 服务端不做任何"下发任意 Intent/命令"的能力 —— 公告一旦能被注入，
 * 就等于给攻击者一个跳板，所以这里只输出数据，解释权永远在客户端白名单。
 *
 * 字段说明（items 里每一项）：
 *   id        必填，稳定唯一（客户端用它记录"已读/已关闭"，改 id 会让用户重新看到）
 *   level     info | warn | critical | update（决定卡片配色）
 *   title     必填，标题
 *   body      正文（纯文本，支持 \n 换行）
 *   link      可选，http/https 链接
 *   linkText  可选，链接按钮文案
 *   action    可选，白名单动作名
 *   actionText可选，动作按钮文案
 *   from      可选，YYYY-MM-DD，早于该日期不展示
 *   until     可选，YYYY-MM-DD，晚于该日期不展示（留空=长期有效）
 */
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

$file = __DIR__ . '/announcements.json';
if (!is_file($file)) {
    // 没有公告文件不算错误：返回空列表，客户端什么都不显示
    echo json_encode(['ok' => true, 'items' => []], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

$data = json_decode((string)file_get_contents($file), true);
if (!is_array($data)) {
    http_response_code(500);
    echo json_encode(['ok' => false, 'error' => 'bad_announcements']);
    exit;
}

$raw = $data['items'] ?? $data;          // 允许直接是一个数组，或 {"items": [...]}
if (!is_array($raw)) {
    $raw = [];
}

$today = date('Y-m-d');
$items = [];
foreach ($raw as $item) {
    if (!is_array($item)) {
        continue;
    }
    $id = trim((string)($item['id'] ?? ''));
    $title = trim((string)($item['title'] ?? ''));
    if ($id === '' || $title === '') {
        continue;                          // 缺 id/标题的条目直接忽略，不让客户端拿到半成品
    }

    // 生效区间：from 之后、until 之前（含当天）
    $from = trim((string)($item['from'] ?? ''));
    $until = trim((string)($item['until'] ?? ''));
    if ($from !== '' && $today < $from) {
        continue;
    }
    if ($until !== '' && $today > $until) {
        continue;
    }

    $items[] = [
        'id' => $id,
        'level' => (string)($item['level'] ?? 'info'),
        'title' => $title,
        'body' => (string)($item['body'] ?? ''),
        'link' => (string)($item['link'] ?? ''),
        'linkText' => (string)($item['linkText'] ?? ''),
        'action' => (string)($item['action'] ?? ''),
        'actionText' => (string)($item['actionText'] ?? ''),
    ];
}

// 顺序即展示顺序：数据文件里越靠前越优先，客户端按数组顺序渲染
echo json_encode(
    ['ok' => true, 'count' => count($items), 'items' => $items],
    JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES
);
