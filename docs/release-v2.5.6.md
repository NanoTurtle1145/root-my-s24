# RootMyS24 v2.5.6 发布说明

> 免解锁免刷机 Root —— 只靠一个 App，纯软绕过三星 KNOX 防护，物理内存漏洞一键提权。

## 版本要点（v2.5.6）编译号 89

- 固件版本按地区分组：**国行 / 港版 / 台版（预留）**
- **禁用 DZG1 独立选项**：DZG1 载荷在真机上早期无声崩溃，且与 DZF2 同构建号，直接走 DZF2 载荷
- BYH7 pipe gate v6（slide=0 防御 + CFI 触发重试 5 次）

## 下载

- **APK**: `RootMyS24-v2.5.6-release.apk`（v2.5.6, versionCode 89, 签名 CN=S9280Root）
- **SHA256**: `371e9a0c2baa5515623f0d17186f4819016bad042f545627e488b5d4da96a162`
- **包名**: `cn.nanoturtle.rootmys9280`
- **系统要求**: Android 11+（无线调试授权）/ Android 8+（Shizuku 授权）
- **适用**: SM-S24 系列（S9210 / S9260 / S9280）

> 版本号自 v2.5.1 起改为三段式。

### 已知问题

- **BYH7（One UI 7）**：pipe gate v6 已更新，仍待真机确认 root 阶段
- **台版**：预留（待适配），选择页置灰不可选
- **exploit 概率性**：失败/重启后重试即可；运行期间建议熄屏降低内核竞态概率
- **临时 root**：每次重启后需重新运行一次「开始 Root」

---

## v2.5.6 更新亮点

### 1. 固件版本按地区分组（国行 / 港版 / 台版预留）

版本选择页按地区分组展示，避免混淆：

| 地区 | 版本 | 载荷 | 范围 |
|---|---|---|---|
| **国行** | DZF2 | `cve-2026-43499` | DZE2–DZG1 |
| **国行** | BYH7 | `cve-2026-43499-byh7` | BYH7 |
| **港版** | DZE2 | `cve-2026-43499-dze2`（独立载荷） | DZE2–DZG1 |
| **港版** | CZA1 | `cve-2026-43499-cza1` | CZA1 |
| **台版** | 预留 | — | 待适配 |

- 港版 DZE2 范围与国行一致（DZE2–DZG1），但载荷**保持独立**：港版 DZE2 的 `kmalloc_caches=0x176c6f8` 与国行 `0x176cbb8` 不同，不能用 DZF2 载荷
- 主界面版本卡显示「地区 · 版本」前缀，一眼区分当前选择
- 台版为预留占位（置灰不可选），后续适配只需加回枚举与载荷

### 2. 禁用 DZG1 独立选项

- DZG1 载荷在真机上启动早期无声崩溃（App 侧表现为「失败: null」，exploit 在 slide-kaslr-ok 后无 stderr 消失，第二次运行崩溃点更早，呈残留污染特征）
- DZG1 与 DZF2 同构建号 3254743、函数符号一致，DZG1 设备直接用 DZF2 载荷覆盖
- 老用户持久化选择过 DZG1 的自动回退到 DZF2

### 3. BYH7 pipe gate v6 载荷

- slide=0 防御：KASLR 滑动为 0 时避免误判
- CFI 触发重试 5 次：提高 One UI 7 目标命中率

---

## 使用方法

1. 安装 App，授权方式：
   - **Shizuku**（默认）：启动 Shizuku App，App 内授权
   - **无线调试**（实验性）：设置页开启「无线调试授权」后，主页可选：
     - 通知配对：开启无线调试 → 点「通知配对」→ 通知栏输 6 位码
     - 配对码：系统「使用配对码配对设备」→ App 输入 IP + 37xxx 端口 + 6 位码
     - 直连：App 输入系统显示的 IP:端口（首次设备上点允许 RSA 指纹）
2. 选择你的**固件版本**（按地区分组列表）
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
- 无线调试为实验性功能，默认隐藏；端口每次开关会变化
- 台版预留待适配；DZG1 设备请选「国行 DZF2」
- 本工具仅供学习研究，请勿用于非法用途

---

## 更新日志

```
v2.5.6 (2026-08-23, versionCode 89)
  + 固件版本按地区分组（国行/港版/台版预留）
  + 禁用 DZG1 独立选项（DZG1 直接走 DZF2 载荷，同构建号 3254743）
  + BYH7 pipe gate v6（slide=0 防御 + CFI 触发重试 5 次）
v2.5.5 (2026-08-22, versionCode 78)
  + 无线调试改为设置页实验性开关（默认隐藏，开启后主页才显示控件）
  + 通知配对改 PairingReplyService 后台处理（不打断系统设置配对码页面）
  + AdbMdns 端口有效性检查（跳过过期 mDNS 缓存）+ IPv6 回环连接尝试
  + 修复 AdbMdns 内部类方法自我递归导致 StackOverflow 闪退
  + mDNS resolved.host null fallback + 发现失败可见通知
  + BYH7 pipe gate v4（cache_match 放宽接受任何非零 slab_cache）
  + 港版 DZG1（One UI 8.5）与 CZA1（One UI 8.0, kernel 6.1.128，待测）适配
  + KernelSU late-load unshare EPERM fallback（新 root helper, md5 1a79f8b4）
  + ADB 通道关闭握手与队列 NPE 修复（CLSE 双向关闭，EOF 判定改由 exitCode）
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
```

---

> 返回 [文档中心](README.md) · [根 README](../README.md)
