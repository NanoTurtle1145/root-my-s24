#!/usr/bin/env python3
"""按机型系列 + 实测状态把载荷 / KSU 驱动分发到各 Gradle flavor 的 assets 目录。

拆包规则（2026-10-26 起）：
    all            全部受管资产（= 原完整包，作为“一个包搞定”的回退）
    s24/s25/...    该系列**已实测**的载荷 + 对应驱动（官方包，小且安全）
    exp            全部**未实测**载荷 + 对应驱动（跨系列，给测试者）

「已实测」按 App 运行时同一套语义在构建时快照：服务端 payloads.json 里
tested=true 优先，清单里没有则回落 enum 的代码默认值。
快照文件 app/tested_payloads.json（提交进仓库，`--refresh-tested` 刷新）。

用法：
    python3 tools/split_assets.py --check            # 只报告
    python3 tools/split_assets.py                    # 执行分发（幂等）
    python3 tools/split_assets.py --refresh-tested   # 先从服务端刷新实测快照再分发
"""
import argparse
import json
import os
import re
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS_MAIN = os.path.join(ROOT, 'app/src/main/assets')
ENUM_KT = os.path.join(
    ROOT, 'app/src/main/kotlin/cn/nanoturtle/rootmys9280/manager/rootmy/RootViewModel.kt')
TESTED_SNAPSHOT = os.path.join(ROOT, 'app/tested_payloads.json')
ENTRIES_JSON = os.path.join(ROOT, 'app/series_entries.json')
MANIFEST_JSON = os.path.join(ROOT, 'app/series_assets_manifest.json')
PAYLOADS_URL = 'https://blog.nanoturtle.cn/rms24_api/payloads.json'

SERIES_FLAVORS = ['s24', 's25', 's23', 's26', 'fold', 'w']
ALL_FLAVORS = ['all'] + SERIES_FLAVORS + ['exp']

# 全系列共用、始终留在 main 的文件
UNIVERSAL_FILES = {'cve-2026-43499-root', 'cve-2026-43499', 'gpl-2.0.txt', 'gpl-3.0.txt'}

# 未在 enum 里按条目引用、但运行时按需加载的资产（必须内置）
EXTRA_ALL = [
    'ksud-czb2-32601', 'ksud-dzf2-32601', 'ksud-dzh3-32601',
    'ksud-bakasu-dzh3', 'bakasu-manager.apk', 'cve-2026-43499-root-bakasu',
    'dfr_lkm-android12-5.10.ko',
]

ENTRY_RE = re.compile(
    r'^\s{8}([A-Z][A-Z0-9_]*)\(\s*"([^"]+)",\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)",'
    r'\s*Region\.([A-Z_]+),\s*"([^"]*)"(?:,\s*tested\s*=\s*(true|false))?', re.M)


def series_of_device(device: str) -> str:
    d = device
    if 'Z Flip' in d or 'Z Fold' in d or d.startswith('SM-F'):
        return 'fold'
    if '心系天下' in d or d.startswith('W9'):
        return 'w'
    if 'S91' in d:
        return 's23'
    if 'S92' in d:
        return 's24'
    if 'S93' in d:
        return 's25'
    if 'S94' in d:
        return 's26'
    return 's24'


def scan_entries():
    src = open(ENUM_KT, encoding='utf-8').read()
    rows = ENTRY_RE.findall(src)
    if not rows:
        sys.exit('未能从 RootViewModel.kt 解析出 FirmwareVersion 条目')
    out = []
    for name, asset, oneui, device, rng, region, ksud, code_tested in rows:
        out.append({
            'name': name, 'asset': asset, 'device': device, 'ksud': ksud,
            'series': series_of_device(device),
            'code_tested': code_tested == 'true',
        })
    return out


def fetch_tested(entries, refresh):
    """返回 {条目名: bool}；服务端优先、回落代码默认。"""
    if refresh or not os.path.isfile(TESTED_SNAPSHOT):
        print('  从服务端刷新实测快照: %s' % PAYLOADS_URL)
        raw = urllib.request.urlopen(PAYLOADS_URL, timeout=30).read().decode('utf-8')
        server = json.loads(raw).get('entries', {})
        snap = {
            'fetched_at': time.strftime('%Y-%m-%d %H:%M:%S'),
            'source': PAYLOADS_URL,
            'server_entries': {k: bool((v or {}).get('tested')) for k, v in server.items()},
        }
        with open(TESTED_SNAPSHOT, 'w', encoding='utf-8') as fh:
            json.dump(snap, fh, ensure_ascii=False, indent=2)
            fh.write('\n')
    snap = json.load(open(TESTED_SNAPSHOT, encoding='utf-8'))
    server = snap.get('server_entries', {})
    print('  实测快照: %s（服务端 %d 条）' % (snap.get('fetched_at', '?'), len(server)))
    return {e['name']: (server[e['name']] if e['name'] in server else e['code_tested'])
            for e in entries}


def list_assets():
    return [n for n in sorted(os.listdir(ASSETS_MAIN))
            if os.path.isfile(os.path.join(ASSETS_MAIN, n))]


def build_plan(entries, verified):
    files = set(list_assets())
    per = {f: set() for f in ALL_FLAVORS}
    entries_of = {f: [] for f in ALL_FLAVORS}

    for e in entries:
        if verified[e['name']]:
            per[e['series']].add(e['asset'])
            per[e['series']].add(e['ksud'])
            entries_of[e['series']].append(e['name'])
        # 未实测的载荷不进任何官方系列包，只随 exp / all
        entries_of['all'].append(e['name'])
        entries_of['exp'].append(e['name'])

    # all = 所有系列包并集 + 未实测资产 + 特殊资产
    for f in SERIES_FLAVORS:
        per['all'] |= per[f]
    for e in entries:
        if not verified[e['name']]:
            per['all'].add(e['asset'])
            per['all'].add(e['ksud'])
    per['all'] |= set(EXTRA_ALL)

    # exp = 全量包（资产与 all 相同），区别只在「默认打开未经测试开关」与版本后缀
    per['exp'] = set(per['all'])

    for f in ALL_FLAVORS:
        per[f] &= files
    return per, entries_of


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--check', action='store_true')
    ap.add_argument('--refresh-tested', action='store_true')
    args = ap.parse_args()

    entries = scan_entries()
    verified = fetch_tested(entries, args.refresh_tested)
    plan, entries_of = build_plan(entries, verified)

    files = list_assets()
    referenced = set()
    for s in plan.values():
        referenced |= s
    universal = {n for n in files if n in UNIVERSAL_FILES or n not in referenced}

    def mb(ns):
        tot = 0
        for n in ns:
            p = os.path.join(ASSETS_MAIN, n)
            if os.path.exists(p):
                tot += os.path.getsize(p)
        return tot / 1048576

    nver = sum(1 for v in verified.values() if v)
    print('\n条目 %d 个：已实测 %d / 未实测 %d' % (len(entries), nver, len(entries) - nver))
    print('main 保留（共用）: %s' % ', '.join(sorted(universal)) or '(无)')
    print()
    print('%-6s %5s %9s  %s' % ('flavor', '文件', '大小', '条目'))
    for f in ALL_FLAVORS:
        print('%-6s %5d %7.1f MB  %d 条' % (f, len(plan[f]), mb(plan[f]), len(entries_of[f])))

    for f in ALL_FLAVORS:
        missing = [n for n in plan[f] if n not in files]
        if missing:
            sys.exit('!! %s 引用了不存在的资产: %s' % (f, ', '.join(missing)))

    if args.check:
        print('\n--check：未改动文件')
        return 0

    for f in ALL_FLAVORS:
        dst = os.path.join(ROOT, 'app/src', f, 'assets')
        os.makedirs(dst, exist_ok=True)
        for n in os.listdir(dst):
            if n not in plan[f]:
                p = os.path.join(dst, n)
                if os.path.isfile(p) or os.path.islink(p):
                    os.remove(p)
        for n in sorted(plan[f]):
            src = os.path.join(ASSETS_MAIN, n)
            target = os.path.join(dst, n)
            if os.path.exists(target):
                os.remove(target)
            try:
                os.link(src, target)
            except OSError:
                import shutil
                shutil.copy2(src, target)

    for n in sorted(plan['all']):
        if n in universal:
            continue
        p = os.path.join(ASSETS_MAIN, n)
        if os.path.exists(p):
            os.remove(p)

    manifest = {'generated_by': 'tools/split_assets.py',
                'universal': sorted(universal),
                'flavors': {f: sorted(plan[f]) for f in ALL_FLAVORS}}
    entries_doc = {'generated_by': 'tools/split_assets.py',
                   'tested_snapshot': os.path.relpath(TESTED_SNAPSHOT, ROOT),
                   'flavors': {f: sorted(entries_of[f]) for f in ALL_FLAVORS}}
    for path, data in ((MANIFEST_JSON, manifest), (ENTRIES_JSON, entries_doc)):
        with open(path, 'w', encoding='utf-8') as fh:
            json.dump(data, fh, ensure_ascii=False, indent=2)
            fh.write('\n')
    print('\n已分发。清单: app/series_assets_manifest.json + app/series_entries.json')
    return 0


if __name__ == '__main__':
    sys.exit(main())
