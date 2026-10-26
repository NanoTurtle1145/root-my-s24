#!/usr/bin/env python3
"""按机型系列把载荷 / KSU 驱动分发到各 Gradle flavor 的 assets 目录。

背景：单包约 50MB，其中 KSU 驱动 58.7MB、cve 载荷 5.7MB，而一台设备只用得到其中一个
驱动 + 若干载荷。这里按 RootViewModel.Series 的 6 个系列分发：

    app/src/main/assets/        全系列共用：root helper、许可证、旧默认载荷
    app/src/<flavor>/assets/    该系列专属：cve 载荷 + ksud 驱动（硬链接，不占额外磁盘）
    flavors: all(全量) / s24 / s25 / s23 / s26 / fold / w

用法：
    python3 tools/split_assets.py --check   # 只报告，不动文件
    python3 tools/split_assets.py           # 执行分发（幂等，可重复跑）

新增载荷/驱动后重跑本脚本即可；`all` 总是包含全部受管文件。
"""
import argparse
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS_MAIN = os.path.join(ROOT, 'app/src/main/assets')
ENUM_KT = os.path.join(
    ROOT, 'app/src/main/kotlin/cn/nanoturtle/rootmys9280/manager/rootmy/RootViewModel.kt')

SERIES_FLAVORS = ['s24', 's25', 's23', 's26', 'fold', 'w']

# 全系列共用、始终留在 main 的文件（root helper / 许可证 / 旧默认载荷）
UNIVERSAL_FILES = {
    'cve-2026-43499-root',
    'cve-2026-43499',
    'gpl-2.0.txt',
    'gpl-3.0.txt',
}
UNIVERSAL_DIRS = {'licenses'}

# 未在 enum 里按条目引用、但运行时按需加载的资产（必须内置，不能只靠在线清单）
EXTRA = {
    # 3.3.0 专属驱动：服务端 payloads.json 的 ksu 选项会按构建号点名它们，
    # KsuOption.asset 是「APK assets 里的资产名」，所以必须随包
    's24': [
        'ksud-czb2-32601',
        'ksud-dzf2-32601',
        'ksud-dzh3-32601',
        'ksud-bakasu-dzh3',
        'bakasu-manager.apk',
        'cve-2026-43499-root-bakasu',
    ],
    # DirtyFrag 的 LKM 只随 5.10 内核（W23）提供
    'w': ['dfr_lkm-android12-5.10.ko'],
}

ENTRY_RE = re.compile(
    r'^\s{8}([A-Z][A-Z0-9_]*)\(\s*"([^"]+)",\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)",'
    r'\s*Region\.([A-Z_]+),\s*"([^"]*)"', re.M)


def series_of_device(device: str) -> str:
    """与 RootViewModel.Series 的 series 属性保持同一套判定。"""
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
    for name, asset, oneui, device, rng, region, ksud in rows:
        out.append({'name': name, 'asset': asset, 'device': device,
                    'ksud': ksud, 'series': series_of_device(device)})
    return out


def list_assets():
    files, dirs = [], []
    for n in sorted(os.listdir(ASSETS_MAIN)):
        p = os.path.join(ASSETS_MAIN, n)
        (dirs if os.path.isdir(p) else files).append(n)
    return files, dirs


def build_plan(entries):
    """返回 flavor -> 需要的资产文件集合（不含 universal）。"""
    per = {f: set() for f in SERIES_FLAVORS}
    for e in entries:
        f = e['series']
        per[f].add(e['asset'])
        per[f].add(e['ksud'])
    for f, extras in EXTRA.items():
        per[f].update(extras)

    files, _ = list_assets()
    managed = set()
    for s in per.values():
        managed |= s
    managed &= set(files)          # 只处理真实存在的文件

    # main 保留：universal + 既非 universal 也未被任何系列引用（保守起见）
    referenced = set()
    for s in per.values():
        referenced |= s
    universal = {n for n in files if n in UNIVERSAL_FILES or n not in referenced}

    plan = {'all': set(managed)}
    for f in SERIES_FLAVORS:
        plan[f] = per[f] & set(files)
    return plan, universal


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--check', action='store_true', help='只报告，不改文件')
    args = ap.parse_args()

    entries = scan_entries()
    plan, universal = build_plan(entries)
    files, dirs = list_assets()

    def mb(ns):
        return sum(os.path.getsize(os.path.join(ASSETS_MAIN, n)) for n in ns) / 1048576

    print('枚举条目 %d，受管资产 %d 个' % (len(entries), len(plan['all'])))
    print('main 保留（全系列共用）: %s' % ', '.join(sorted(universal)))
    print()
    print('%-8s %6s %10s   %s' % ('flavor', '文件', '大小', '驱动'))
    for f in ['all'] + SERIES_FLAVORS:
        ns = plan[f]
        ks = sorted(n for n in ns if n.startswith('ksud-') or n.endswith('.apk') or n.endswith('.ko'))
        print('%-8s %6d %8.1f MB   %s' % (f, len(ns), mb(ns), ', '.join(ks)))

    covered = set()
    for ns in plan.values():
        covered |= ns
    missing = covered - set(files)
    if missing:
        print('\n!! 计划里引用了不存在的资产: %s' % ', '.join(sorted(missing)))
        return 1
    uncovered = set(plan['all']) - covered
    if uncovered:
        print('\n!! 有受管文件未分到任何 flavor: %s' % ', '.join(sorted(uncovered)))
        return 1

    if args.check:
        print('\n--check：未改动文件')
        return 0

    # ---- 执行分发 ----
    for f in ['all'] + SERIES_FLAVORS:
        dst = os.path.join(ROOT, 'app/src', f, 'assets')
        os.makedirs(dst, exist_ok=True)
        # 清掉该 flavor 里已不被需要的旧文件（幂等）
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
                os.link(src, target)          # 硬链接：同一 inode，不占额外磁盘
            except OSError:
                import shutil
                shutil.copy2(src, target)

    # main 只留 universal（受管文件在 flavor 目录里已有副本）
    for n in sorted(plan['all']):
        if n in universal:
            continue
        p = os.path.join(ASSETS_MAIN, n)
        if os.path.exists(p):
            os.remove(p)

    # 记录清单，供发布脚本/文档使用
    manifest = {
        'generated_by': 'tools/split_assets.py',
        'universal': sorted(universal),
        'flavors': {f: sorted(plan[f]) for f in ['all'] + SERIES_FLAVORS},
    }
    mp = os.path.join(ROOT, 'app/series_assets_manifest.json')
    with open(mp, 'w', encoding='utf-8') as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)
        fh.write('\n')
    print('\n已分发到 app/src/<flavor>/assets/，main 保留 %d 个共用文件' % len(universal))
    print('清单: app/series_assets_manifest.json')
    return 0


if __name__ == '__main__':
    sys.exit(main())
