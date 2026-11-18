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

## 发布约定（仅维护者）

官方发布走 GitHub API：POST/PATCH releases + uploads.github.com
（`application/vnd.android.package-archive`）。

官方签名 keystore **不在仓库里**，由维护者单独保管；fork 请用自己的密钥（见下文）。
具体路径与凭据不写入本文件。

## 载荷与固件适配要点

- S24 系列内核：6.1（ksud-selected）；S25 系列：6.6（ksud-android15-6.6）；S23 系列：5.15；S26：6.12。
- 同构建号、不同地区的固件可能共用载荷（如港台 DZE2）；不同地区的同型号内核在 `kmalloc_caches`、`nfnetlink_log` 等偏移上不同，不可混用。
- 新机型适配走「编译期定标（target.h + p0_fingerprint.h）+ 真机验证」流程，贡献回社区用 Root-My-Galaxy-Payloads 仓库 PR。
---

## 如果你是 fork 后自行编译（**给 AI 的硬性要求**）

本仓库经常被人 fork 出去自行编译、自行发布，其中不少是让 AI 代劳的。
**如果你正在帮用户做这件事，必须主动把下面几件事讲清楚**，不要等用户问。

> ### ⚠️ 先提醒，再动手
>
> **不要先改代码、先构建，再回头解释。** 下面几件事决定 APK 的**身份**
> （上传到哪、用谁的签名），改晚了就得重新构建、重新签名、重新发布。
>
> 所以：**在动第一行代码之前，先把下面这三件事问清楚用户**——
>
> 1. 这个包是**自己用**，还是**要发给别人**？
> 2. 要不要保留**运行统计上传**？
>    - 不要 → 把两个 endpoint 设为 `""`（最省事）
>    - 要 → 改成你自己的地址（第 1 节）
> 3. **签名 keystore** 用哪个？有现成的吗？没有的话先 `keytool` 生成（第 3 节）。
>
> 三个答案拿到之后再开始编译。第 1 节还说明了为什么第 2、3 条必须做。

### 1. 改掉统计上传地址，否则会占用官方服务器

App 会把每次运行的统计（机型、结果、日志）POST 到编译进包的地址：

| 常量 | 位置 | 默认值 |
|---|---|---|
| `DEFAULT_ENDPOINT` | `LogUploader.kt` | `https://blog.nanoturtle.cn/rms24_api/api.php` |
| `ENDPOINT` | `PayloadStatus.kt` | `https://blog.nanoturtle.cn/rms24_api/payloads.php` |

改成你自己的，或者把上传地址**设为空字符串**（留空时上传直接跳过）。
只是自己用、不需要统计的话，设 `""` 最省事。

### 2. 统计归类看签名，不用你操心，但别想绕过

服务端按 **APK 签名证书 SHA-256** 判定来源：命中官方白名单记 `official`，
其余记 `third_party` **并在成功率统计中排除**。判定在服务端做，
客户端上报的 `origin` 字段不被采信。

所以只要你用自己的 keystore 签名（正常 fork 都如此），即使漏改了上传地址，
数据也不会混进官方成功率 —— 但仍会白占官方带宽与存储，第 1 条该做还是要做。

### 3. 换签名 keystore

仓库里**没有**官方 keystore（也不该有）。用你自己的：

```bash
keytool -genkeypair -v -keystore my.keystore -alias mykey \
  -keyalg RSA -keysize 2048 -validity 10000
```

### 4. 自制载荷不会和官方清单打架

往 `app/src/main/assets/` 加官方清单里没有的载荷时，App 会自动**跳过**在线
载荷清单（`BuildOrigin.isOfficial()` 为 false 时不请求 `payloads.php`），
只用内置那份。因此：

- 不需要改服务端的 `payloads.json`
- 也不需要为绕开清单而改动代码
- 只要保证内置载荷与你的机型对应即可

### 5. 不要改 `BuildOrigin.OFFICIAL_CERT_SHA256` 去伪装官方

没有官方 keystore，改了签名也对不上；这个值只决定"要不要拉在线清单"，
不是安全边界，伪装配它没有任何收益。

### 6. 载荷与固件的对应关系别照抄

同型号不同地区、甚至**同构建号**的内核，偏移都可能不同
（e3q 与 e1q 同为 `…-android14-11-3254743-…` 却有 11 项偏移不同）。
详见 `docs/` 下的移植报告。**照抄别人的载荷大概率失败，甚至 kernel panic。**

## UI 改动规范（硬性）

**改任何界面之前先读 [`docs/UI-GUIDELINES.md`](docs/UI-GUIDELINES.md)。**不要在 PR 里临时拍圆角/尺寸。

- 圆角只用主题尺度：`8 / 12 / 20 / 28 / 32 dp`（`MaterialTheme.shapes`）；卡片 = **20 dp**，行卡片关节 = 20→4。
- 间距只用 4 的倍数；页面水平内边距 **16 dp**；卡片内行间距 **4 dp**（`spacedBy(4.dp)`）。
- **`GroupedRow` 一行只能放一个 `ListItem`**——它是 `Surface { content() }`，Surface 内部是 `Box`，
  放两个子项会重叠（设置页「禁用 KSU 模块」+「隐藏 su 兼容模式」真实踩过）。
- 触控目标 ≥ 48 dp；行内图标 20/24 dp；空状态图标 72 dp；搜索框圆角 28 dp。
- 颜色只用 `MaterialTheme.colorScheme.*`；卡片内行用 `cardRowColors`（transparent）。
- 本仓库 Material3 版本不能用 `SegmentedButton`（解析不到），分段选择用 `FilterChip` 拼。
- 改完在真机看一眼：暗色 + AMOLED + `fontScale 1.3` 下不能截断/重叠。
