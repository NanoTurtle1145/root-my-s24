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

## 与本地发布脚本的关系

`tools/release_flavors.py` 仍是**主发布路径**（会同时上传服务器 `dl/` 并写 `latest.json`，
国内下载走镜像）。GitHub Actions 是备份/验证路径：产物可下载，Release 可独立发布。
服务器上传未放进 CI（需要 SSH 凭据），如以后需要可加 `RMS24_SSH_*` secrets 并扩展 workflow。
