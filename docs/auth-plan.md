# RootMyS24 授权方案规划（v2.4）

> 目标：让用户在无电脑环境下也能完成 Root 前置授权。
> **方案二（无线调试直连授权）已实现（2026-08-22）**：设置页/主页授权卡片可切换
> Shizuku 或无线调试，无线调试走 ADB 协议实现。
> **v2.4（2026-08-22）**：新增 adb pair 配对码授权（TLS + SPAKE2），
> 参考 Shizuku 的无线调试授权方式，连接免 RSA 弹窗。
> 本文档保留为方案记录，标记已实现/未实现。

---

## 现状（2026-08-22 更新）

| 环节 | 实现 | 依赖 |
|---|---|---|
| Shizuku 授权 | `ShizukuController.requestPermission()` | 手机已装 Shizuku App 且已通过 ADB 启动 |
| 无线调试授权 ✅ | `AdbWirelessController`（ADB 协议 + 配对） | Android 11+ 开发者选项无线调试，无需任何 App |
| 命令执行 | `ShellExecutor` 接口（Shizuku / AdbWireless 两实现可切换） | 授权方式选择持久化在设置 |

**痛点（已解决）**：
1. ~~用户需额外安装 Shizuku App~~ → 无线调试方式零安装
2. ~~Shizuku 启动仍需无线调试配对或电脑 adb~~ → App 内直接走 ADB 协议认证
3. ~~两步授权割裂~~ → 授权卡片内完成连接，Root 流程统一走 `shellExecutor`
4. ~~首次连接需点 RSA 指纹弹窗~~ → adb pair 配对码授权，配对后免弹窗

---

## 方案一：Shizuku 授权（已实现）

> 现状保持：Shizuku 仍是可用的提权通道之一（需装 Shizuku App）。
> 与方案二并存，用户在授权卡片二选一，默认 Shizuku。

---

## 方案二：无线调试直连授权（已实现 ✅）

**实现方式**：`AdbWirelessController.kt` —— ADB 协议客户端：

- **配对（v2.4 新增）**：`pair(host, pairPort, code)` 走 TLS + SPAKE2
  （`AdbPairingClient.kt` + 内置 `libadb.so`），输入无线调试界面
  「使用配对码配对设备」的 37xxx 配对端口 + 6 位配对码，把本机 RSA 公钥
  预授权给设备（写入 authorized_keys），之后 connect 免 RSA 弹窗。
  与 `adb pair` / Shizuku 完全相同的流程。
- **认证**：支持 STLS（CNXN → A_STLS → TLS 升级，已配对免弹窗）与经典
  AUTH token 挑战（RSA-SHA1 签名 + Android 二进制公钥，未配对时设备弹
  RSA 指纹确认框）两条路径；密钥对持久化在 App 私有目录 `files/adb/`。
- **密钥/证书**：`AdbKey.kt`（参考 Shizuku）—— RSA-2048 + Android 二进制
  RSAPublicKey 524B 结构（base64 + " name\0"）+ 自签名 X509（CN=00）
  TLSv1.3 客户端证书（BouncyCastle 生成）。
- **shell 通道**：`OPEN shell:<cmd>` → OKAY → WRTE 双向数据 → CLSE
  （adbd 以 `sh -c` 执行，支持 cd/env/&&；pty 输出过滤 `\r`）
- **进程模型**：全局 reader 线程统一读 socket，按 remoteId 分发到各 `AdbProcess`，
  与 `ShizukuController` 的 `Process` 行为一致（`drainProcessOutput` 轮询可用）
- **安全**：无 shell 权限风险（adb shell = uid 2000，与 Shizuku 同等级）；
  无线调试端口由用户从系统设置读取输入（39xxx 连接端口，无需 mDNS）

**组件清单（v2.4）**：
- `AdbPairingClient.kt`：配对客户端（TLS + SPAKE2 消息交换 + PeerInfo 公钥交换）
- `AdbKey.kt`：密钥对 + Android 二进制公钥 + TLS SSLContext/证书
- `PairingContext.kt`（`moe.shizuku.manager.adb`）：libadb.so 的 JNI 封装
  （SPAKE2/HKDF/AES-GCM，类名必须与 Shizuku 一致）
- `jniLibs/arm64-v8a/libadb.so`：提取自 Shizuku 13.6.0（Apache-2.0，许可文本
  在 `assets/licenses/SHIZUKU-APACHE-2.0.txt`）
- Conscrypt `exportKeyingMaterial` 用反射调用（hidden API，避免编译期依赖）

**用户流程（v2.4）**：
```
1. 开发者选项 → 开启「无线调试」
2. App 主页授权卡片 → 选「无线调试」→ 输 IP + 配对端口(37xxx) + 6 位配对码 → 配对
3. 显示「配对成功 ✓」→ 输连接端口(39xxx) → 连接
4. 显示「已连接 ✓」→ 开始 Root（全程无需 RSA 弹窗）
```

**UI**：RootFlowScreen 的 AuthCard 新增配对区（IP + 配对端口 + 配对码 + 配对按钮）。

### 未实现（后续可选）
- mDNS 自动发现设备 IP:端口（当前手动输入）
- 会话持久化（root 后自动断连、下次自动重连）
- 非 arm64 ABI 的 libadb.so（当前仅打包 arm64-v8a，与 App 目标设备一致）
