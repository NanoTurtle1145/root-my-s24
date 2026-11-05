#!/usr/bin/env python3
"""RootMyS24 发布交互脚本 —— 驱动 GitHub Actions 流水线（.github/workflows/release.yml）

用法：
    python3 tools/ci_release.py            # 交互菜单
    python3 tools/ci_release.py --status   # 只看最近运行状态
    python3 tools/ci_release.py --build --channel beta --flavors s24 [--publish --tag v3.5.5]
    python3 tools/ci_release.py --tag v3.5.5            # 打 tag 并推（自动触发正式发布）
    python3 tools/ci_release.py --download [run_id]     # 下载产物到 dist/

依赖：仅标准库；令牌从 ~/.config/dsh/github.token 读取（不打印、不落日志）。
"""
import argparse, json, os, subprocess, sys, time, urllib.request, urllib.error, zipfile, io

REPO = 'NanoTurtle1145/root-my-s24'
TOKEN_PATH = os.path.expanduser('~/.config/dsh/github.token')
WORKFLOW = 'release.yml'
FLAVORS = ['all', 's24', 'fold', 'exp', 's25', 's23', 's26', 'w', 'gamma']
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def token():
    try:
        return open(TOKEN_PATH, encoding='utf-8').read().strip()
    except OSError:
        sys.exit('找不到令牌文件：%s（见 docs/ci-release.md）' % TOKEN_PATH)


def api(method, url, data=None, raw=False):
    req = urllib.request.Request(
        url,
        data=json.dumps(data).encode() if data is not None else None,
        method=method,
        headers={
            'Authorization': 'Bearer ' + token(),
            'Accept': 'application/vnd.github+json',
            'User-Agent': 'rootmys24-ci-release',
            'Content-Type': 'application/json',
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            body = r.read()
            return body if raw else json.loads(body.decode() or '{}')
    except urllib.error.HTTPError as e:
        detail = e.read().decode('utf-8', 'replace')[:400]
        sys.exit('GitHub API %s %s -> %s: %s' % (method, url, e.code, detail))


def git(*args):
    r = subprocess.run(['git'] + list(args), cwd=ROOT, capture_output=True, text=True)
    return r.returncode, (r.stdout or '') + (r.stderr or '')


# ---------- 运行状态 ----------

def latest_runs(limit=5):
    return api('GET', 'https://api.github.com/repos/%s/actions/workflows/%s/runs?per_page=%d'
               % (REPO, WORKFLOW, limit))['workflow_runs']


def run_steps(run_id):
    jobs = api('GET', 'https://api.github.com/repos/%s/actions/runs/%s/jobs' % (REPO, run_id))['jobs']
    out = []
    for job in jobs:
        for s in job.get('steps', []):
            out.append((s['number'], s['name'], s['status'], s['conclusion']))
    return out


def print_run(run, verbose=False):
    print('  run %s  %-10s %-10s %s' % (run['id'], run['status'], run['conclusion'], run['created_at']))
    print('    %s' % run['html_url'])
    if verbose:
        for num, name, st, con in run_steps(run['id']):
            print('     %2d. %-46s %-10s %s' % (num, name, st, con))


def wait_run(run_id, timeout=120 * 60):
    start = time.time()
    last = ''
    while True:
        run = api('GET', 'https://api.github.com/repos/%s/actions/runs/%s' % (REPO, run_id))
        cur = ''
        for num, name, st, con in run_steps(run_id):
            if st == 'in_progress':
                cur = name
                break
        if cur != last and cur:
            print('    [%4ds] %s' % (int(time.time() - start), cur))
            last = cur
        if run['status'] == 'completed':
            return run
        if time.time() - start > timeout:
            sys.exit('等待超时（%ds），请到 Actions 页面查看：%s' % (timeout, run['html_url']))
        time.sleep(15)


# ---------- 动作 ----------

def dispatch_build(channel, flavors, publish, tag):
    body = {'ref': 'main', 'inputs': {
        'channel': channel, 'flavors': ','.join(flavors),
        'publish': 'true' if publish else 'false', 'tag': tag,
    }}
    api('POST', 'https://api.github.com/repos/%s/actions/workflows/%s/dispatches' % (REPO, WORKFLOW), body)
    time.sleep(5)
    return latest_runs(1)[0]


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None  # 交给调用方手动处理


def get_artifact_zip(url):
    """下载 artifact zip。

    GitHub 会 302 到带签名查询串的 blob 存储；那个 URL **不能再带 Authorization**，
    否则 Azure 返回 InvalidAuthenticationInfo。所以先拦下 302，再匿名取签名 URL。
    """
    req = urllib.request.Request(url, headers={
        'Authorization': 'Bearer ' + token(),
        'Accept': 'application/vnd.github+json',
        'User-Agent': 'rootmys24-ci-release',
    })
    opener = urllib.request.build_opener(_NoRedirect)
    try:
        with opener.open(req, timeout=120) as r:
            return r.read()
    except urllib.error.HTTPError as e:
        if e.code not in (301, 302, 303, 307, 308):
            sys.exit('下载 artifact 失败：%s %s' % (e.code, e.read().decode('utf-8', 'replace')[:300]))
        loc = e.headers.get('Location')
        if not loc:
            sys.exit('下载 artifact 失败：302 但没有 Location')
        with urllib.request.urlopen(loc, timeout=300) as r2:
            return r2.read()


def download_artifacts(run_id, dest=None):
    dest = dest or os.path.join(ROOT, 'dist')
    os.makedirs(dest, exist_ok=True)
    arts = api('GET', 'https://api.github.com/repos/%s/actions/runs/%s/artifacts' % (REPO, run_id))['artifacts']
    if not arts:
        print('  该 run 没有产物')
        return
    for a in arts:
        print('  下载 %s（%.1f MB）…' % (a['name'], a['size_in_bytes'] / 1048576))
        data = get_artifact_zip(a['archive_download_url'])
        z = zipfile.ZipFile(io.BytesIO(data))
        z.extractall(dest)
        print('    -> %s' % dest)
    # GitHub 的 artifact zip 会多包一层目录，这里把里面的 apk 提出来
    for root, _dirs, files in os.walk(dest):
        for f in files:
            if f.endswith('.apk') and root != dest:
                src = os.path.join(root, f)
                dst = os.path.join(dest, f)
                if src != dst and not os.path.exists(dst):
                    os.replace(src, dst)
    print('  完成：%s' % ', '.join(sorted(os.path.basename(p) for p in
                                     [os.path.join(dest, x) for x in os.listdir(dest)] if p.endswith('.apk'))))


def push_tag(tag):
    if not tag.startswith('v'):
        tag = 'v' + tag
    rc, out = git('tag', '--list', tag)
    if out.strip():
        sys.exit('tag 已存在：%s' % tag)
    rc, out = git('tag', tag)
    if rc != 0:
        sys.exit('创建 tag 失败：%s' % out.strip())
    rc, out = git('push', 'origin', tag)
    if rc != 0:
        sys.exit('推送 tag 失败：%s' % out.strip())
    print('  已推送 tag %s，等待 Actions 触发…' % tag)
    time.sleep(10)
    for r in latest_runs(3):
        if r.get('event') == 'push' and r.get('head_branch') == tag:
            return r
    return latest_runs(1)[0]


# ---------- 交互菜单 ----------

def ask(prompt, default=''):
    v = input(prompt + (' [%s] ' % default if default else ' ')).strip()
    return v or default


def menu():
    while True:
        print('\n=== RootMyS24 Actions 发布 ===')
        runs = latest_runs(3)
        for r in runs:
            print_run(r)
        print('''
  1) 构建（不发布，验证用）
  2) 构建 + 创建 GitHub Release
  3) 打 tag 并推送（正式发布）
  4) 刷新状态
  5) 下载最近一次 run 的产物
  0) 退出''')
        choice = ask('选择:')
        try:
            if choice == '0':
                return 0
            if choice == '4':
                continue
            if choice == '1' or choice == '2':
                channel = ask('渠道 beta/stable:', 'beta')
                if channel not in ('beta', 'stable'):
                    print('  无效渠道'); continue
                default_flavors = 'all,s24,fold,exp'
                flavors = [x.strip() for x in ask('flavors（逗号分隔）:', default_flavors).split(',') if x.strip()]
                bad = [f for f in flavors if f not in FLAVORS]
                if bad:
                    print('  未知 flavor：%s' % bad); continue
                publish = choice == '2'
                tag = ''
                if publish:
                    tag = ask('发布 tag（如 v3.5.5）:')
                    if not tag:
                        print('  发布需要 tag'); continue
                print('  触发中：channel=%s flavors=%s publish=%s tag=%s'
                      % (channel, flavors, publish, tag or '-'))
                run = dispatch_build(channel, flavors, publish, tag)
                print('  run %s 已启动' % run['id'])
                run = wait_run(run['id'])
                print_run(run, verbose=True)
                if run['conclusion'] != 'success':
                    print('  ✗ 构建失败，日志：%s' % run['html_url'])
                    continue
                if publish:
                    print('  ✓ 已发布 Release：https://github.com/%s/releases/tag/%s' % (REPO, tag))
                if ask('下载产物？(y/N):', 'N').lower() == 'y':
                    download_artifacts(run['id'])
            elif choice == '3':
                tag = ask('新 tag（如 v3.5.5）:')
                if not tag:
                    continue
                if ask('确认推送 tag %s 并触发正式发布？(y/N):' % tag, 'N').lower() != 'y':
                    continue
                run = push_tag(tag)
                run = wait_run(run['id'])
                print_run(run, verbose=True)
                if run['conclusion'] == 'success':
                    print('  ✓ Release: https://github.com/%s/releases/tag/%s' % (REPO, tag))
                else:
                    print('  ✗ 失败，日志：%s' % run['html_url'])
            elif choice == '5':
                run_id = runs[0]['id'] if runs else None
                if not run_id:
                    print('  没有可下载的 run'); continue
                print('  下载 run %s 的产物…' % run_id)
                download_artifacts(run_id)
            else:
                print('  未知选项')
        except KeyboardInterrupt:
            print('\n  已取消当前操作')
        except Exception as e:
            print('  操作失败：%s' % e)


def main():
    ap = argparse.ArgumentParser(description='RootMyS24 GitHub Actions 发布交互脚本')
    ap.add_argument('--status', action='store_true', help='打印最近运行状态')
    ap.add_argument('--build', action='store_true', help='直接构建')
    ap.add_argument('--channel', default='beta')
    ap.add_argument('--flavors', default='all,s24,fold,exp')
    ap.add_argument('--publish', action='store_true')
    ap.add_argument('--tag', default='')
    ap.add_argument('--download', nargs='?', const='latest', default=None)
    args = ap.parse_args()

    if args.status:
        for r in latest_runs(5):
            print_run(r, verbose=True)
        return 0
    if args.download:
        rid = latest_runs(1)[0]['id'] if args.download == 'latest' else int(args.download)
        download_artifacts(rid)
        return 0
    if args.build:
        flavors = [x.strip() for x in args.flavors.split(',') if x.strip()]
        run = dispatch_build(args.channel, flavors, args.publish, args.tag)
        run = wait_run(run['id'])
        print_run(run, verbose=True)
        return 0 if run['conclusion'] == 'success' else 1
    return menu()


if __name__ == '__main__':
    sys.exit(main())
