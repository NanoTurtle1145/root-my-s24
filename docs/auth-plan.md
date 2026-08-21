# RootMyS24 授权方案规划（v2.3）

> 目标：让用户在无电脑环境下也能完成 Root 前置授权。
> 当前仅支持"Shizuku 授权"（需先装 Shizuku App 或无线调试授权），
> 规划补齐"无线调试授权"（adb wireless 直连授权）作为内置替代方案。

---

## 现状

| 环节 | 实现 | 依赖 |
|---|---|---|
| Shizuku 授权 | `ShizukuController.requestPermission()` | 手机已装 Shizuku App 且已通过 ADB 启动 |
| 命令执行 | `ShizukuController.exec/shell/capture` | Shizuku binder 在运行（shell 权限） |

**痛点**：
1. 用户需**额外安装 Shizuku App**（`moe.shizuku.privileged.api`）
2. Shizuku 启动仍需无线调试配对或电脑 adb
3. 两步授权割裂，小白易卡在"Shizuku 未运行"

---

## 方案一：Shizuku 授权（现状增强）

**思路**：保持 Shizuku 作为唯一提权通道，把"授权引导"做成 App 内向导。

### 流程
```
1. 检查 Shizuku binder → 未运行则提示
2. 引导用户在系统"开发者选项 → 无线调试"开启，并记住配对码/配对 IP:端口
3. App 内引导：adb pair 配对 → adb connect 连接 → adb shell sh /data/local/tmp/start_shizuku.sh
4. Shizuku 启动后，Shizuku.requestPermission() 弹出系统授权框
5. 授权完成，进入 Root 流程
```

### 关键点
- 配对码只能看一次（系统生成），需引导用户**提前截图**或记下
- 配对用 mDNS：`adb pair IP:PORT 配对码`，连接用 `adb connect IP:PORT`
- Android 11+ 无线调试的配对端口（37xxx）与连接端口（39xxx）**不同**，需分别读取
- 系统设置页无直接 deep link 到"无线调试"子页 → 需引导用户手动到"开发者选项"

### 优点
- 改动小：`ShizukuController` 已完备，只加引导 UI
- Shizuku 生态成熟，binder 稳定

### 缺点
- 用户仍需装 Shizuku App
- 多一步"启动 Shizuku"的前置操作

---

## 方案二：无线调试直连授权（内置替代，推荐）

**思路**：不依赖 Shizuku App，App 直接通过 `adb connect` 获得 shell 权限后，
用 `su` / 直接 shell 执行 Root 流程；或复用 Android 11+ 的 `adb` 授权模型。

### 流程
```
1. 用户开启"开发者选项 → 无线调试"（无需装任何 App）
2. App 内输入配对码（系统显示 6 位码）或配对 IP:端口
3. App 调用 adb pair → adb connect（利用 libadb 或 Java 的 ADB 协议实现）
4. 连接后 App 进程以 shell 身份执行 Root 流程（等价于现在的 Shizuku shell）
5. 流程完成，断开连接（可选保留）
```

### 关键点
- **需要 ADB 协议实现**：Java/Kotlin 端实现 mDNS 发现 + `adb pair`（RSA 握手）+ `adb connect` + shell 通道
  - 可参考：`adb` 开源协议的 Java 移植（如 `com.android.tools` 的 ddmlib、`adb-connection` 库）
  - 或 App 内置一个 `adb` 静态二进制（arm64），`ProcessBuilder` 调用
- **无 shell 权限风险**：adb shell 是 shell uid(2000)，与 Shizuku 相同权限等级
- **断开时机**：Root 成功后立即断开（exploit 期间保持连接即可）
- **安卓 11+**：无线调试需 RSA 指纹授权（首次弹窗点允许），App 需处理
- **安全**：配对码一次性，连接会话有效期内可用；App 内存中保存 RSA key

### 优点
- 用户**零额外安装**（不装 Shizuku）
- 一条路径直达 Root，授权引导更短
- 可与方案一并存（用户已有 Shizuku 则走 Shizuku，否则走无线调试）

### 缺点
- 实现量大：需移植 ADB 协议（pair/connect/shell）或内嵌 adb 二进制
- 无线调试环境不稳定（WiFi 掉线、端口变化）

---

## 推荐路线

### 短期（v2.3 可做）
- **方案一增强**：App 内"无线调试授权引导"——引导用户开启无线调试、输入配对码，
  App 内置 `adb` 二进制（从 platform-tools 提取 arm64 版，~5MB）执行
  `adb pair` → `adb connect` → `adb shell sh /data/local/tmp/start_shizuku.sh`
  → 回到 App 继续 Shizuku 授权。**改动集中在新增一个"授权向导"页面**。

### 中期（v2.4+）
- **方案二**：去掉 Shizuku 依赖，App 内置 adb 通道直接执行 Root 流程。
  需要把 `ShizukuController` 抽象为 `ShellExecutor` 接口，
  Shizuku 与 adb 两个实现可切换。

### 长期
- mDNS 自动发现设备，免手动输 IP：端口
- 会话持久化（root 后自动断连，下次 root 自动重连）

---

## 文件改动预估

| 文件 | 改动 |
|---|---|
| `ShizukuController.kt` | 抽 `ShellExecutor` 接口；新增 `AdbShellExecutor` |
| `AdbAuthWizard.kt`（新） | 授权向导 UI（配对码输入、进度、错误提示） |
| `assets/adb`（新） | arm64 静态 adb 二进制（或 libadb JNI） |
| `RootViewModel.kt` | 授权状态机：Shizuku 可用 → 走 Shizuku；否则引导无线调试 |
| `strings.xml` | 向导文案（中/英） |

---

## 风险与对策

| 风险 | 对策 |
|---|---|
| adb 二进制体积（~5MB） | 从 platform-tools 提取，strip 后约 4MB，可接受 |
| 无线调试端口变化 | 每次连接重新读取 `adb pair` 输出中的端口 |
| RSA 指纹冲突 | 连接前 `adb kill-server` 清状态；首次授权弹窗引导点允许 |
| 用户无电脑但不会开无线调试 | 向导图文步骤，逐屏截图引导 |
