<?php
/**
 * RootMyS24 载荷状态接口（讨论区同目录的 rms24_api/）
 *
 * 给 App 的机型选择页用，解决两件事：
 *
 *   1. **「已实测」不再烧死在 APK 里**。改一次 `payloads.json`，所有已装 App 立刻看到新结论。
 *   2. **下发"这台固件能用哪些 KSU 驱动版本"**。内核 vermagic 必须对得上，只有服务端知道
 *      某个版本为哪些固件构建过；App 只负责列出来让用户选。
 *
 * 数据来源两部分：
 *   - `payloads.json`：人工维护的结论（tested / note / ksu 清单 / 该条目对应的构建码）
 *   - `run_logs`：真实运行日志里聚合出来的成功/失败次数与最近成功时间（窗口可配）
 *
 * 数据库不可用时**照常返回配置**（只是没有统计）——这个接口挂了不该让机型页变空。
 * 未在 payloads.json 里出现的条目，App 会沿用包内内置值（tested=null 即"服务端不表态"）。
 */
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');

$file = __DIR__ . '/payloads.json';
if (!is_file($file)) {
    http_response_code(503);
    echo json_encode(['ok' => false, 'error' => 'no_config']);
    exit;
}
$doc = json_decode((string)file_get_contents($file), true);
if (!is_array($doc)) {
    http_response_code(500);
    echo json_encode(['ok' => false, 'error' => 'bad_config']);
    exit;
}

$entries = (array)($doc['entries'] ?? []);
$windowDays = max(1, min(365, (int)($doc['window_days'] ?? 60)));

/** 聚合某个条目对应构建码的真实运行结果；拿不到数据库就返回空统计。 */
$stats = [];
try {
    require_once __DIR__ . '/forum_db.php';
    $pdo = rms24_db();
    foreach ($entries as $key => $entry) {
        $ok = 0;
        $fail = 0;
        $lastOk = '';
        foreach ((array)($entry['builds'] ?? []) as $pattern) {
            $pattern = trim((string)$pattern);
            if ($pattern === '') {
                continue;
            }
            $suffix = str_replace('*', '', $pattern);
            $where = str_contains($pattern, '*') ? 'build_tag LIKE ?' : 'build_tag = ?';
            $arg = str_contains($pattern, '*') ? '%' . $suffix : $pattern;
            // 口径与管理端一致：**只有真正进入过 exploit 的运行（tried=1）才算失败**。
            // 没跑起来的那些（Shizuku 没就绪、推送失败等）不是漏洞利用失败，混进来会把
            // 成功率压得毫无参考价值（DZH3 实测：151 条 succeeded=0 里只有 89 条 tried=1）。
            $st = $pdo->prepare(
                'SELECT SUM(CASE WHEN succeeded = 1 THEN 1 ELSE 0 END) AS ok,
                        SUM(CASE WHEN succeeded = 0 AND tried = 1 THEN 1 ELSE 0 END) AS fail,
                        MAX(CASE WHEN succeeded = 1 THEN received_at END) AS last_ok
                 FROM run_logs
                 WHERE received_at > NOW() - INTERVAL ' . $windowDays . ' DAY AND ' . $where
            );
            $st->execute([$arg]);
            $row = $st->fetch() ?: [];
            $ok += (int)($row['ok'] ?? 0);
            $fail += (int)($row['fail'] ?? 0);
            $when = (string)($row['last_ok'] ?? '');
            if ($when !== '' && $when > $lastOk) {
                $lastOk = $when;
            }
        }
        $stats[(string)$key] = ['ok' => $ok, 'fail' => $fail, 'lastOk' => substr($lastOk, 0, 10)];
    }
} catch (Throwable $e) {
    // 无数据库/表缺失：只出配置，App 侧没有统计行而已
    $stats = [];
}

$out = ['ok' => true, 'updated' => (string)($doc['updated'] ?? date('c')), 'windowDays' => $windowDays, 'entries' => []];
foreach ($entries as $key => $entry) {
    $key = (string)$key;
    if ($key === '' || !is_array($entry)) {
        continue;
    }
    $item = [];
    if (array_key_exists('tested', $entry)) {
        $item['tested'] = (bool)$entry['tested'];
    }
    $note = trim((string)($entry['note'] ?? ''));
    if ($note !== '') {
        $item['note'] = $note;
    }
    $ksu = [];
    foreach ((array)($entry['ksu'] ?? []) as $option) {
        if (!is_array($option)) {
            continue;
        }
        $asset = trim((string)($option['asset'] ?? ''));
        // 资产名形状校验：App 侧还会再校验一次，这里先挡住明显不对的
        if (!preg_match('/^ksud-[A-Za-z0-9._-]{1,60}$/', $asset)) {
            continue;
        }
        $item = [
            'asset' => $asset,
            'label' => trim((string)($option['label'] ?? '')) ?: $asset,
            'state' => trim((string)($option['state'] ?? '')) ?: 'candidate',
        ];
        // 版本号是 App 侧"按版本选择"的依据；适用设备决定它会不会出现在选项里
        $version = trim((string)($option['version'] ?? ''));
        if ($version !== '') {
            $item['version'] = $version;
        }
        $devices = [];
        foreach ((array)($option['devices'] ?? []) as $dev) {
            $dev = trim((string)$dev);
            if ($dev !== '') {
                $devices[] = $dev;
            }
        }
        if ($devices) {
            $item['devices'] = $devices;
        }
        $ksu[] = $item;
    }
    if ($ksu) {
        $item['ksu'] = $ksu;
    }
    $stat = $stats[$key] ?? ['ok' => 0, 'fail' => 0, 'lastOk' => ''];
    $item['ok'] = (int)$stat['ok'];
    $item['fail'] = (int)$stat['fail'];
    $item['lastOk'] = (string)$stat['lastOk'];
    $out['entries'][$key] = $item;
}

$json = json_encode($out, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
$etag = '"' . substr(hash('sha256', $json), 0, 16) . '"';
header('Cache-Control: public, max-age=300');
header('ETag: ' . $etag);
if (($_SERVER['HTTP_IF_NONE_MATCH'] ?? '') === $etag) {
    http_response_code(304);
    exit;
}
echo $json;
