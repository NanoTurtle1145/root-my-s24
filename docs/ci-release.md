# GitHub Actions 发布流水线

`.github/workflows/release.yml` 负责签名构建分系列 APK，并可选地创建 GitHub Release。

## 触发方式

| 触发 | 行为 |
|---|---|
| push tag `v*` | 自动构建 + 创建 Release；tag 含 `-`（如 `v3.5.5-beta.240`）→ 预发布，裸版本号（`v3.5.5`）→ 正式 |
| 手动 `workflow_dispatch` | 指定 `channel` / `flavors` / 是否发布 / 发布 tag |

## 必需的仓库 Secrets

位置：仓库 Settings → Secrets and variables → Actions → Repository secrets。

| Secret | 内容 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `s9280.keystore` 的 base64（`base64 -w0 s9280.keystore`） |
| `ANDROID_STORE_PASSWORD` | 密钥库密码 |
| `ANDROID_KEY_ALIAS` | 密钥别名 |
| `ANDROID_KEY_PASSWORD` | 密钥密码 |

> 本地构建时这些值放在 `~/.gradle/gradle.properties`；CI 里由 workflow 追加到工程 `gradle.properties`，
> 构建结束后随 runner 销毁，不会进入仓库。

## 构建参数

- JDK 21、Android SDK `platforms;android-37.0` + `build-tools;37.0.0` + NDK `29.0.14206865`
- CMake 由 `pip install cmake ninja` 提供（工程要求 ≥ 3.29.8）
- `versionCode` = `git rev-list --count origin/main`，`versionName` = 最新正式 tag +（渠道后缀）
  → CI 必须 `fetch-depth: 0`，workflow 已处理
- 默认发布集合：`all,s24,fold,exp`（与 `tools/release_flavors.py` 一致）

## 产物

`dist/RootMyS24-v<ver>-build<code>[-<flavor>].apk` + `dist/SHA256SUMS.txt`，
以 Actions Artifact 形式保留 14 天；发布时同时上传到 GitHub Release。

## 交互脚本

```sh
python3 tools/ci_release.py            # 菜单：构建 / 构建+发布 / 打 tag / 刷新状态 / 下载产物
python3 tools/ci_release.py --status   # 查看最近运行
python3 tools/ci_release.py --build --channel beta --flavors s24
python3 tools/ci_release.py --tag v3.5.5          # 打 tag 并推送（自动发布）
python3 tools/ci_release.py --download latest      # 下载最近 run 产物到 dist/
```

令牌从 `~/.config/dsh/github.token` 读取，不落日志；artifact 下载会自动处理
GitHub 的 302 签名跳转（跳转后的 blob 地址不能携带 Authorization）。

## 与本地发布脚本的关系

`tools/release_flavors.py` 仍是**主发布路径**（会同时上传服务器 `dl/` 并写 `latest.json`，
国内下载走镜像）。GitHub Actions 是备份/验证路径：产物可下载，Release 可独立发布。
服务器上传未放进 CI（需要 SSH 凭据），如以后需要可加 `RMS24_SSH_*` secrets 并扩展 workflow。

## nightly（每日构建）

`.github/workflows/nightly.yml` 每天从 `main` 自动构建一版**未发布**产物，用来尽早发现 main 挂掉：

| 项 | 值 |
|---|---|
| 触发 | 每天 **19:30 UTC**（= 次日 03:30 Asia/Shanghai）+ 手动 `workflow_dispatch`<br>避开整点：GitHub schedule 在整点最拥堵，实测可能延迟数小时 |
| 渠道 | 固定 **`nightly`**（versionName 形如 `3.6.4-nightly.307`；dispatch 可改 alpha/beta） |
| 构建 | 默认 `s24,all` 两个 release flavor |
| 签名 | 有 secrets → 官方 keystore；没有 → **debug 签名**并打 warning（fork 也能跑，只是不能分发） |
| 产物 | `dist/RootMyS24-nightly-<YYYYMMDD>-<sha8>-v<ver>-build<code>[-flavor].apk` + `SHA256SUMS.txt` |
| 发布 | **滚动 pre-release**：tag/release 名都是 `nightly`，每次删旧建新（prerelease + 附 APK），`v*` 前缀不触发 `release.yml` |
| 保留 | Actions Artifact 7 天；`nightly` release 每日覆盖（不占用正式发布历史） |

本地冒烟等价命令：

```sh
./gradlew :app:assembleS24Release -Prms24Channel=nightly
# versionName 形如 3.6.4-nightly.307-s24

# 渠道白名单（build.gradle.kts 校验，写错会直接构建失败）：
#   stable / alpha / beta / nightly
```

> nightly 的 versionName 会带 `-alpha.<提交数>`，与正式版/预发布版区分；看到 alpha 号不对，先检查
> 最近一次 tag 是否推上去了（base 版本来自 tag）。
