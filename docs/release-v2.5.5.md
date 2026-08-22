# RootMyS24 v2.5.5 发布说明

> 免解锁免刷机 Root —— 只靠一个 App，纯软绕过三星 KNOX 防护，物理内存漏洞一键提权。

## 下载

- **APK**: `RootMyS24-v2.5.5-release.apk`（v2.5.5, versionCode 71, 签名 CN=S9280Root）
- **SHA256**: `f7db73d0c377c377bf490fae49a052cef5237c48861d50c8ccb5891d64e3d429`
- **包名**: `cn.nanoturtle.rootmys9280`
- **系统要求**: Android 11+（无线调试授权）/ Android 8+（Shizuku 授权）
- **适用**: SM-S24 系列（S9210 / S9260 / S9280）

> 版本号自 v2.5.1 起改为三段式。

### 已知问题

- **BYH7（One UI 7）**：已定标但待真机验证；真机跑不到 root 阶段时多为 struct page 偏移漂移，日志判读见 [run-log-analysis.md](run-log-analysis.md)
- **无线调试（实验性，默认关闭）**：端口每次开关无线调试会变化；设置页开启后主页才显示相关控件
- **exploit 概率性**：失败/重启后重试即可；运行期间建议熄屏降低内核竞态概率
- **临时 root**：每次重启后需重新运行一次「开始 Root」

---

## v2.5.5 更新亮点

### 1. 无线调试改为设置页实验性开关（默认关闭）

无线调试一直不稳定，本版将其收敛为**实验性功能**：

- 设置页「运行」分组新增**「无线调试授权」开关**，默认**关闭**
- 关闭时主页只显示 Shizuku 授权，不显示任何无线调试控件（配对/连接/通知配对全部隐藏）
- 开启后主页才显示无线调试单选与配对控件
- 关闭开关时若当前授权方式为无线调试，自动回退 Shizuku 并断开连接

### 2. 修复 AdbMdns 内部递归导致 StackOverflow 闪退

`onServiceFound/onServiceLost` 在内部类 `AdbDiscoveryListener` 里与构造参数同名，内部调用被 Kotlin 解析到自身方法 → 无限递归。修复：构造参数重命名为 `onServiceDiscovered` 等，消除重名。

### 3. 修复 mDNS 解析失败链

- `resolved.host` 为 null 时 fallback `127.0.0.1`
- 发现失败产生**可见通知**（不再静默）
- 通知配对时第一条通知（搜索中）与第二条通知（输入配对码）均能正常出现

### 4. 港版 CZA1 适配（One UI 8.0, kernel 6.1.128）

新增港版 CZA1 目标（构建 33419968 系列），独立载荷支持。

### 5. 无线调试通知配对（v2.5.2 引入，保留）

- `AdbMdns`：NsdManager 自动发现 `_adb-tls-pairing` / `_adb-tls-connect`
- `AdbPairingFlow`：通知栏 RemoteInput 直接输入 6 位配对码 → 配对 → 自动连接
- Android 13+ 通知权限主动请求（v2.5.3）

### 6. 无线调试配对码授权（v2.5.1 引入，保留）

TLS + SPAKE2 + PeerInfo 公钥交换，内置 `libadb.so`（Shizuku 13.6.0，Apache-2.0）。

### 7. BYH7 pipe gate v2 载荷（v2.5.1 引入，保留）

`kmalloc_caches` 逐槽位读取 + 64 页 × 16 slabs 扫描，修正 `log_payload` 偏移描述 `0x176cbb8 → 0x16bad78`。

---

## 使用方法

1. 安装 App，授权方式：
   - **Shizuku**（默认）：启动 Shizuku App，App 内授权
   - **无线调试**（实验性）：设置页开启「无线调试授权」后，主页可选：
     - 通知配对：开启无线调试 → 点「通知配对」→ 通知栏输 6 位码
     - 配对码：系统「使用配对码配对设备」→ App 输入 IP + 37xxx 端口 + 6 位码
     - 直连：App 输入系统显示的 IP:端口（首次设备上点允许 RSA 指纹）
2. 选择你的**固件版本**（列表视图）
3. 点「开始 Root」——App 自动推送载荷 → LD_PRELOAD 触发 CVE-2026-43499 → 物理内存读写 → KASLR 绕过 → SELinux 降级 → root UMH → KernelSU late-load
4. 看到「Root 成功」即完成，重启后需重新执行（不可持久化）

---

## 技术栈（简要）

- **漏洞**: CVE-2026-43499（pipe physrw 物理内存读写）
- **链**: pipe 原语 → KASLR slide 检测（tracefs / boot_id 双路径）→ SELinux 降级 → `call_usermodehelper_exec` 提权 → KernelSU 加载
- **内核**: 6.1.99（BYH7）/ 6.1.128（CZA1）/ 6.1.145（DZE2–DZG1）
- **平台**: e1q（S9210）/ e3q（S9280）
- **授权**: Shizuku binder / 无线调试 ADB 协议（实验性，设置页开关控制）

---

## 注意事项

- 每次重启后需要重新执行 Root（系统更新也会清除）
- 无线调试为实验性功能，默认关闭；端口每次开关会变化
- 港版 DZG1（`S9280ZHS6DZG1`，构建 33419968）符号未知，若失败请提取该机型 boot 供定标
- 本工具仅供学习研究，请勿用于非法用途

---

## 更新日志

```
v2.5.5 (2026-08-22)
  + 无线调试改为设置页实验性开关（默认关闭，开启后主页才显示控件）
  + 修复 AdbMdns 内部类方法自我递归导致 StackOverflow 闪退
  + mDNS resolved.host null fallback + 发现失败可见通知
  + 港版 CZA1 适配（One UI 8.0, kernel 6.1.128）
v2.5.4 (2026-08-22)
  + 修复 mDNS 过滤条件过严导致第二条通知（输入配对码）不出现
  + 去掉 isLocal / isPortBusy 硬性过滤（服务类型 _adb-tls-pairing 足够唯一）
  + resolve 失败自动重试（最多 5 次，间隔 800ms），日志级别 V→I
v2.5.3 (2026-08-22)
  + 修复通知发不出（Android 13+ 通知权限检查与请求，引导拒绝用户去系统设置开启）
  + 所有通知异常从静默吞掉改为 Log.w 输出
v2.5.2 (2026-08-22)
  + 无线调试通知配对（NsdManager 自动发现 + RemoteInput 通知输入配对码）
  + AuthCard 新增「通知配对」按钮（Android 11+，旧系统回退手动输入）
v2.5.1 (2026-08-22)
  + 无线调试配对码授权（TLS + SPAKE2 + libadb.so，免弹窗）
  + BYH7 pipe gate v2 载荷（kmalloc_caches 逐槽位 + 64 页 × 16 slabs 扫描）
  + 修正 log_payload 偏移描述 0x176cbb8 → 0x16bad78
  + 版本号改为三段式
v2.5 (2026-08-22)
  + 无线调试 AUTH 授权（内置 ADB 协议客户端，替代 Shizuku）
  + 日志头部机型报告（设备/固件/KNOX 完整信息）
  + 日志导出按机型命名（rootmys9280-<机型>.txt）
  + 日志恢复可见化（恢复提示行 + 写入失败诊断 + 导出兜底）
  + 设置页新增调试选项「自动保存日志」
v2.2 (2026-08-22)
  + 版本选择列表视图（RadioButton）
  + DZG1 专用载荷（修正 nfulnl_logger 偏移 0x16a6547）
  + BYH7 SELINUX enforcing 偏移修复（0x02420440 → 0x02420441）
  + 修复 release 构建收款码丢失（R8 收缩改为静态引用 + keep.xml）
  + RootFlow 页自动申请 Shizuku 权限（幂等）
  + 收款二维码（微信+支付宝）接入关于页
```
