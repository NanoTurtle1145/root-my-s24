#!/usr/bin/env python3
"""分系列发布：一次构建多个 flavor，上传到同一个 GitHub release，并更新服务器更新清单。

用法：
    python3 tools/release_flavors.py --channel beta --dry-run      # 只构建+校验+生成清单
    python3 tools/release_flavors.py --channel beta                # 构建 + 发布 GitHub + 上传服务器
    python3 tools/release_flavors.py --channel beta --skip-build   # 复用已有产物

产物命名：
    all            RootMyS24-v<ver>-build<code>.apk          （保持旧命名）
    s24/s25/...    RootMyS24-v<ver>-build<code>-<flavor>.apk

清单（写到服务器 dl/latest.json）：
    {"versionName","versionCode","releasedAt","notes","channels":{flavor:{file,sha256,mirror}}}

⚠️ 服务器 dl/ 里的 APK 是**主下载源**（国内访问 GitHub 常失败），
   脚本默认会上传；只想发 GitHub 时加 --skip-server 并把清单里加 "url" 字段。
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = 'NanoTurtle1145/root-my-s24'
SSH_TOOL = os.path.expanduser('~/.config/dsh/rms24_api_ssh.py')
GH_TOKEN = os.path.expanduser('~/.config/dsh/github.token')
REMOTE_DL = '/www/wwwroot/ntblog_cn/rms24_api/dl'
STAGE = '/tmp/rms24-release'

FLAVORS = ['all', 's24', 's25', 's23', 's26', 'fold', 'w', 'exp']
# 默认发布的集合：全量 + 有“已实测”载荷的系列 + 测试包。
# s25/s23/s26/w 目前没有已实测载荷（包内无载荷），等有实测条目后再加。
RELEASE_FLAVORS = ['all', 's24', 'fold', 'exp']
JDK = '/usr/lib/jvm/java-21-openjdk'


def sh(cmd, timeout=1800, env=None):
    e = dict(os.environ)
    e.setdefault('JAVA_HOME', JDK)
    if env:
        e.update(env)
    r = subprocess.run(cmd, shell=isinstance(cmd, str), capture_output=True,
                       text=True, timeout=timeout, cwd=ROOT, env=e)
    return r.returncode, (r.stdout or '') + (r.stderr or '')


def git(*args):
    rc, out = sh(['git'] + list(args), timeout=120)
    return out.strip() if rc == 0 else ''


def apk_path(flavor):
    return os.path.join(ROOT, 'app/build/outputs/apk', flavor, 'release',
                        'app-%s-release.apk' % flavor)


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, 'rb') as fh:
        for b in iter(lambda: fh.read(1 << 20), b''):
            h.update(b)
    return h.hexdigest()


def aapt_badging(apk):
    bt = '/home/nt/项目/samsung_root_research/06_工具链/android-14'
    rc, out = sh([os.path.join(bt, 'aapt'), 'dump', 'badging', apk], timeout=120)
    m = re.search(r"versionCode='(\d+)' versionName='([^']*)'", out)
    return (int(m.group(1)), m.group(2)) if m else (None, None)


def verify_signature(apk):
    bt = '/home/nt/项目/samsung_root_research/06_工具链/android-14'
    rc, out = sh([os.path.join(bt, 'apksigner'), 'verify', '--print-certs', apk],
                 timeout=180, env={'JAVA_HOME': JDK})
    m = re.search(r'certificate SHA-256 digest: ([a-f0-9]+)', out)
    return m.group(1) if m else ''


def build(flavor, channel):
    task = ':app:assemble%sRelease' % (flavor[0].upper() + flavor[1:])
    print('  build %-5s -> %s' % (flavor, task))
    rc, out = sh([os.path.join(ROOT, 'gradlew'), task,
                  '-Prms24Channel=%s' % channel, '--console=plain'], timeout=3600)
    if rc != 0:
        tail = '\n'.join(out.strip().splitlines()[-15:])
        sys.exit('构建失败 %s:\n%s' % (flavor, tail))


def gh_api(method, url, data=None):
    tok = open(GH_TOKEN).read().strip()
    body = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(url, data=body, method=method, headers={
        'Authorization': 'Bearer ' + tok,
        'Accept': 'application/vnd.github+json',
        'User-Agent': 'rootmys24-release',
        'Content-Type': 'application/json',
    })
    try:
        with urllib.request.urlopen(req, timeout=180) as r:
            return r.status, json.loads(r.read().decode() or '{}')
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode() or '{}')


def upload_asset(upload_url, apk, name):
    tok = open(GH_TOKEN).read().strip()
    cmd = ['curl', '-s', '-X', 'POST',
           '-H', 'Authorization: Bearer ' + tok,
           '-H', 'Content-Type: application/vnd.android.package-archive',
           '-H', 'Accept: application/vnd.github+json',
           '--data-binary', '@' + apk,
           '%s?name=%s' % (upload_url, name)]
    rc, out = sh(cmd, timeout=1800)
    try:
        return json.loads(out)
    except Exception:
        return {'state': 'failed', 'raw': out[:200]}


def remote_put(local, remote):
    rc, out = sh(['python3', SSH_TOOL, 'put', local, remote], timeout=3600)
    return rc == 0, out.strip()[-200:]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--channel', default='beta', choices=['alpha', 'alpha1', 'alpha2',
                                                          'beta', 'beta1', 'beta2', 'stable'])
    ap.add_argument('--flavors', default=','.join(RELEASE_FLAVORS))
    ap.add_argument('--dry-run', action='store_true', help='只构建/校验并生成本地清单')
    ap.add_argument('--skip-build', action='store_true')
    ap.add_argument('--skip-github', action='store_true')
    ap.add_argument('--skip-server', action='store_true')
    ap.add_argument('--notes', default='')
    ap.add_argument('--notes-file', default='', help='发布说明 Markdown 文件')
    args = ap.parse_args()

    if args.notes_file:
        args.notes = open(args.notes_file, encoding='utf-8').read()
    flavors = [f.strip() for f in args.flavors.split(',') if f.strip()]
    unknown = [f for f in flavors if f not in FLAVORS]
    if unknown:
        sys.exit('未知 flavor: %s' % ', '.join(unknown))

    version_code = int(git('rev-list', '--count', 'refs/remotes/origin/main') or 0)
    latest_tag = git('describe', '--tags', '--abbrev=0').lstrip('v')
    if not version_code or not latest_tag:
        sys.exit('无法从 git 推导版本（versionCode=%s tag=%s）' % (version_code, latest_tag))
    base_version = re.sub(r'-(alpha|beta|beta1|beta2|stable).*$', '', latest_tag)
    version_name = base_version if args.channel == 'stable' else '%s-%s.%d' % (
        base_version, args.channel, version_code)
    tag = 'v' + version_name
    print('发布版本: %s (versionCode=%d, tag=%s)' % (version_name, version_code, tag))

    os.makedirs(STAGE, exist_ok=True)
    results = {}
    for f in flavors:
        if not args.skip_build:
            build(f, args.channel)
        apk = apk_path(f)
        if not os.path.isfile(apk):
            sys.exit('缺少产物: %s' % apk)
        vc, vn = aapt_badging(apk)
        want_vn = version_name if f == 'all' else version_name + '-' + f
        if vc != version_code or vn != want_vn:
            sys.exit('%s 版本不符: got %s/%s want %d/%s' % (f, vc, vn, version_code, want_vn))
        sig = verify_signature(apk)
        name = ('RootMyS24-v%s-build%d.apk' % (version_name, version_code)
                if f == 'all' else
                'RootMyS24-v%s-build%d-%s.apk' % (version_name, version_code, f))
        staged = os.path.join(STAGE, name)
        shutil.copy2(apk, staged)
        results[f] = {'file': name, 'sha256': sha256_of(staged),
                      'size': os.path.getsize(staged), 'cert': sig}
        print('  %-5s %6.1f MB  %s  cert=%s' % (
            f, results[f]['size'] / 1048576, results[f]['sha256'][:16], sig[:16]))

    manifest = {
        'versionName': version_name,
        'versionCode': version_code,
        'releasedAt': time.strftime('%Y-%m-%d %H:%M:%S'),
        'notes': args.notes,
        'channels': {f: {'file': r['file'], 'sha256': r['sha256']}
                     for f, r in results.items()},
    }
    mpath = os.path.join(STAGE, 'latest.json')
    with open(mpath, 'w', encoding='utf-8') as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)
        fh.write('\n')
    print('\n清单已写出: %s' % mpath)

    if args.dry_run:
        print('--dry-run：未发布')
        return 0

    url = None
    if not args.skip_github:
        rc, rel = gh_api('POST', 'https://api.github.com/repos/%s/releases' % REPO, {
            'tag_name': tag, 'target_commitish': 'main',
            'name': 'RootMyS24 %s' % version_name,
            'body': args.notes or '分系列发布：%s' % ', '.join(flavors),
            'prerelease': args.channel != 'stable', 'draft': False,
        })
        print('GitHub release -> HTTP %d' % rc)
        if rc >= 300:
            sys.exit('创建 release 失败: %s' % json.dumps(rel, ensure_ascii=False)[:300])
        up = rel['upload_url'].split('{')[0]
        url = rel.get('html_url')
        for f, r in results.items():
            a = upload_asset(up, os.path.join(STAGE, r['file']), r['file'])
            print('  upload %-28s %s' % (r['file'], a.get('state')))
            if a.get('state') != 'uploaded':
                sys.exit('上传失败: %s' % json.dumps(a, ensure_ascii=False)[:300])
            manifest['channels'][f]['mirror'] = url
        # 补了 mirror 之后要重写清单，否则传给服务器的还是没 mirror 的旧文件
        with open(mpath, 'w', encoding='utf-8') as fh:
            json.dump(manifest, fh, ensure_ascii=False, indent=2)
            fh.write('\n')

    if not args.skip_server:
        for f, r in results.items():
            ok, msg = remote_put(os.path.join(STAGE, r['file']), REMOTE_DL + '/' + r['file'])
            print('  服务器 %-30s %s' % (r['file'], 'ok' if ok else 'FAIL ' + msg))
            if not ok:
                sys.exit('服务器上传失败: %s' % r['file'])
        ok, msg = remote_put(mpath, REMOTE_DL + '/latest.json')
        print('  服务器 latest.json %s' % ('ok' if ok else 'FAIL ' + msg))
        if not ok:
            sys.exit('清单上传失败')

    print('\n发布完成: %s' % (url or '(仅本地/服务器)'))
    return 0


if __name__ == '__main__':
    sys.exit(main())
