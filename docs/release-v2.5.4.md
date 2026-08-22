# RootMyS24 v2.5.4 发布说明

> 免解锁免刷机 Root —— 只靠一个 App，纯软绕过三星 KNOX 防护，物理内存漏洞一键提权。

## 下载

- **APK**: `RootMyS24-v2.5.4-release.apk`（v2.5.4, versionCode 66, 签名 CN=S9280Root）
- **包名**: `cn.nanoturtle.rootmys9280`
- **适用**: SM-S24 系列（S9210 / S9260 / S9280）

> 版本号自 v2.5.1 起改为三段式。

---

## v2.5.4 更新亮点

### 1. 修复第二条通知（输入配对码）不出现

根本原因：`AdbMdns.onServiceResolved` 的 `isLocal` + `isPortBusy` 硬性过滤在寄生式架构下失效——宿主无 INTERNET 权限时 `getNetworkInterfaces()` 只返回回环地址，设备局域网 IP 不匹配被过滤，`notifyInputCode` 永远不回调。

修复：
- 去掉 `isLocal` / `isPortBusy` 硬性过滤，改为日志输出（`_adb-tls-pairing` 服务类型足够唯一，误连风险极低）
- resolve 失败自动重试（最多 5 次，每次间隔 800ms）
- 日志级别从 V 提升到 I，方便 logcat 排查

### 2. 通知权限修复（v2.5.3 引入，保留）

点击「通知配对」先请求 `POST_NOTIFICATIONS` 权限（Android 13+），拒绝后引导去系统设置开启。

### 3. 无线调试通知配对（v2.5.2 引入，保留）

参考 Shizuku 交互，省去手动输入 IP/端口：

- 新增 `AdbMdns`：NsdManager 自动发现本机 `_adb-tls-pairing` / `_adb-tls-connect` 服务与端口
- 新增 `AdbPairingFlow`：搜索通知 → **RemoteInput 通知栏直接输入 6 位配对码** → 配对 → 自动发现连接端口并 connect → 结果通知
- AuthCard 新增「通知配对」按钮（Android 11+；旧系统自动回退手动输入）
- MainActivity 转发 RemoteInput 结果（寄生式架构下 PendingIntent 送唯一真实组件）

### 2. 无线调试配对码授权（v2.5.1 引入，保留）

TLS + SPAKE2 + PeerInfo 公钥交换，内置 `libadb.so`（Shizuku 13.6.0，Apache-2.0），App 内输入 IP + 37xxx 配对端口 + 6 位配对码授权。

### 3. BYH7 pipe gate v2 载荷（v2.5.1 引入，保留）

`kmalloc_caches` 逐槽位读取 + 64 页 × 16 slabs 扫描，修正 `log_payload` 偏移描述 `0x176cbb8 → 0x16bad78`。

### 4. 无线调试 AUTH 授权（v2.5 引入，保留）

Android 11+ 无线调试直连：App 输入系统显示的 IP:端口，RSA 密钥对走 ADB 协议认证（首次设备弹 RSA 指纹确认框）。

### 5. 日志体系（v2.5 引入，保留）

- 日志头部机型报告（设备/固件/内核/KNOX 完整信息）
- 导出文件名按机型命名（`rootmys9280-<机型>.txt`）
- 崩溃/重启后自动恢复日志（恢复提示 + 写入失败诊断 + 导出兜底）
- 设置页调试选项「自动保存日志」

---

## 使用方法

1. 安装 App，选择授权方式：
   - **无线调试 + 通知配对**（最省事）：设置 → 开发者选项 → 无线调试 → 保持开启 → App 内点「通知配对」，在通知栏直接输入系统显示的 6 位配对码
   - **无线调试 + 配对码**：设置 → 开发者选项 → 无线调试 → 「使用配对码配对设备」→ 记下 IP:端口与 6 位码 → App 内输入连接
   - **无线调试直连**：记下「IP 地址和端口」→ App 内输入（首次需在设备上点允许 RSA 指纹）
   - **Shizuku**：启动 Shizuku App，App 内授权
2. 选择你的**固件版本**（列表视图）
3. 点「开始 Root」——App 自动推送载荷 → LD_PRELOAD 触发 CVE-2026-43499 → 物理内存读写 → KASLR 绕过 → SELinux 降级 → root UMH → KernelSU late-load
4. 看到「Root 成功」即完成，重启后需重新执行（不可持久化）

---

## 技术栈（简要）

- **漏洞**: CVE-2026-43499（pipe physrw 物理内存读写）
- **链**: pipe 原语 → KASLR slide 检测（tracefs / boot_id 双路径）→ SELinux 降级 → `call_usermodehelper_exec` 提权 → KernelSU 加载
- **内核**: 6.1.99（BYH7）/ 6.1.145（DZE2–DZG1）
- **平台**: e1q（S9210）/ e3q（S9280）
- **授权**: Shizuku binder / 无线调试 ADB 协议（内置客户端 + libadb.so）

---

## 注意事项

- 每次重启后需要重新执行 Root（系统更新也会清除）
- 无线调试端口每次开关会变化，重新连接时以系统当前显示为准
- 港版 DZG1（`S9280ZHS6DZG1`，构建 33419968）符号未知，若失败请提取该机型 boot 供定标
- 本工具仅供学习研究，请勿用于非法用途

---

## 更新日志

```
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
