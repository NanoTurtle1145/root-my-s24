# RootMyS24 授权方案规划（v2.3）

> 目标：让用户在无电脑环境下也能完成 Root 前置授权。
> **方案二（无线调试直连授权）已实现（2026-08-22）**：设置页/主页授权卡片可切换
> Shizuku 或无线调试，无线调试走纯 Kotlin ADB 协议实现，无需内置二进制。
> 本文档保留为方案记录，标记已实现/未实现。

---

## 现状（2026-08-22 更新）

| 环节 | 实现 | 依赖 |
|---|---|---|
| Shizuku 授权 | `ShizukuController.requestPermission()` | 手机已装 Shizuku App 且已通过 ADB 启动 |
| 无线调试授权 ✅ | `AdbWirelessController`（纯 Kotlin ADB 协议） | Android 11+ 开发者选项无线调试，无需任何 App |
| 命令执行 | `ShellExecutor` 接口（Shizuku / AdbWireless 两实现可切换） | 授权方式选择持久化在设置 |

**痛点（已解决）**：
1. ~~用户需额外安装 Shizuku App~~ → 无线调试方式零安装
2. ~~Shizuku 启动仍需无线调试配对或电脑 adb~~ → App 内直接走 ADB 协议认证
3. ~~两步授权割裂~~ → 授权卡片内完成连接，Root 流程统一走 `shellExecutor`

---

## 方案一：Shizuku 授权（已实现）

> 现状保持：Shizuku 仍是可用的提权通道之一（需装 Shizuku App）。
> 与方案二并存，用户在授权卡片二选一，默认 Shizuku。

---

## 方案二：无线调试直连授权（已实现 ✅）

**实现方式**：`AdbWirelessController.kt` —— 纯 Kotlin ADB 协议客户端：

- **认证**：CNXN 握手 → AUTH token 挑战 → RSA-SHA1 签名 → 公钥交换（首次设备弹
  RSA 指纹确认框，点允许后完成授权；密钥对持久化在 App 私有目录 `files/adb/`）
- **shell 通道**：`OPEN shell:<cmd>` → OKAY → WRTE 双向数据 → CLSE
  （adbd 以 `sh -c` 执行，支持 cd/env/&&；pty 输出过滤 `\r`）
- **进程模型**：全局 reader 线程统一读 socket，按 remoteId 分发到各 `AdbProcess`，
  与 `ShizukuController` 的 `Process` 行为一致（`drainProcessOutput` 轮询可用）
- **安全**：无 shell 权限风险（adb shell = uid 2000，与 Shizuku 同等级）；
  无线调试端口由用户从系统设置读取输入（39xxx 连接端口，无需 mDNS）

**用户流程**：
```
1. 开发者选项 → 开启「无线调试」，记下 IP:端口（连接端口 39xxx）
2. App 主页授权卡片 → 选「无线调试」→ 输入 IP:端口 → 连接
3. 首次连接设备弹 RSA 指纹确认 → 点允许
4. 显示「已连接 ✓」→ 开始 Root
```

**UI**：RootFlowScreen 新增 AuthCard（授权方式单选 + 无线调试连接表单）。

### 未实现（后续可选）
- mDNS 自动发现设备 IP:端口（当前手动输入）
- 会话持久化（root 后自动断连、下次自动重连）
