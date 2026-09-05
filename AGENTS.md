# RootMyS24 项目提示词

RootMyS24 免解锁 Root 工具的开发提示。与 `~/.dsh/AGENTS.md`（本机 Android 工具链全局说明）配合使用。

## 版本号规则（x.x.x）

版本号采用语义化 `x.y.z`，由 git tag 决定 `versionName`，提交数决定 `versionCode`：

- **第 1 位（大版本）**：第二位自然进位（如 9→10、99→100）或重大架构/框架更新时 +1。
- **第 2 位（中版本）**：比第 1 位更新更频繁，通常是**机型适配**（如新增某系列/某地区固件支持）。
- **第 3 位（小版本）**：小 bug 修复、语言/文案类更新（例如 PR 合并的某语言翻译补全、文案修正）。

举例：v3.1.0 是机型适配版本；某次语言翻译补全对应的小版本更新（如 v3.1.1）。发布 APK 命名统一带 build 号：`RootMyS24-v{版本}-build{versionCode}.apk`。

## 版本号来源（勿硬编码、勿提交）

- `versionCode` = `git rev-list --count refs/remotes/origin/main`（GitCommitCountValueSource）。
- `versionName` = 最近的 git tag（GitLatestTagValueSource，如 `v3.1.0` → `3.1.0`）。
- 两者均由构建时从 git 推导，**不在源码里硬编码**；相关 `.gitignore` 已排除本地 `local.properties`、`*.keystore`、`*.log` 等，切勿把本地路径/签名/版本快照文件提交到仓库。

## 发布约定

- git push 一律走 HTTPS + token（`https://x-access-token:{TOKEN}@github.com/NanoTurtle1145/root-my-s24.git`）。
- Release 发布用 GitHub API：POST/PATCH releases + uploads.github.com（`application/vnd.android.package-archive`）。
- 签名 keystore：`/home/nt/.android/s9280.keystore`（s9280root / s9280root / CN=S9280Root）。

## 载荷与固件适配要点

- S24 系列内核：6.1（ksud-selected）；S25 系列：6.6（ksud-android15-6.6）；S23 系列：5.15；S26：6.12。
- 同构建号、不同地区的固件可能共用载荷（如港台 DZE2）；不同地区的同型号内核在 `kmalloc_caches`、`nfnetlink_log` 等偏移上不同，不可混用。
- 新机型适配走「编译期定标（target.h + p0_fingerprint.h）+ 真机验证」流程，贡献回社区用 Root-My-Galaxy-Payloads 仓库 PR。