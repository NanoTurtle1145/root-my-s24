# RootMyS24 v2.2 发布说明

> 免解锁免刷机 Root —— 只靠一个 App，纯软绕过三星 KNOX 防护，物理内存漏洞一键提权。

## 📦 下载

- **APK**: `app-release.apk`（v2.2, versionCode 41, 签名 CN=S9280Root）
- **包名**: `cn.nanoturtle.rootmys9280`
- **适用**: SM-S24 系列（S9210 / S9260 / S9280）

---

## ✨ v2.2 更新亮点

### 1. 版本选择改为列表视图，支持范围一目了然

不再需要猜测固件版本——打开 App 直接看到三个选项：

| 选项 | 适配固件 | 载荷 |
|---|---|---|
| **One UI 8.5 / DZE2 – DZF2** | S9280 全系早期 6.1.145 | `cve-2026-43499` |
| **One UI 8.5 / DZG1** | S9280 DZG1（新固件） | `cve-2026-43499-dzg1`（专用） |
| **One UI 7 / BYH7** | S9210 BYH7（One UI 7） | `cve-2026-43499-byh7`（专用） |

> 之前「DZF2 一个载荷通吃 DZE2–DZG1」——实测 DZG1 上 DZF2 载荷成功率偏低（概率性失败），本版为 DZG1 **单独定标**，成功率显著提升。

### 2. BYH7（One UI 7）修复确定性问题

- **根因**：BYH7 内核（6.1.99）的 `struct selinux_state` 布局与 DZF2（6.1.145）不同——`enforcing` 字段偏移 +1。原载荷把 `selinux_state+0` 清零只禁用了 `disabled` 标志，SELinux 依旧 enforcing，导致 root helper 执行被拒（`retval=-13`，30 次全失败）。
- **修复**：定标到 `enforcing` 真正所在偏移（`0x02420441`），反汇编验证 `selinux_bprm_creds_for_exec` 钩子读取位置一致。
- **结果**：BYH7 从「确定性失败」变为「可成功」。

### 3. DZG1 专用定标

- DZG1（`S9280ZCS6DZG1`，构建 3254743）与 DZF2 同构建号，函数/数据符号全部一致；
- 但 `nfulnl_logger.name` 字符串偏移不同（DZF2 的 `0x16a61b8` 是复制 S928U1 的错位值），本版用 DZG1 正确值 `0x16a6547`，boot_id 泄漏路径修复。

---

## 🚀 使用方法

1. 安装 App，授予 **Shizuku** 权限（需先启动 Shizuku，无线调试或电脑 adb 均可）
2. 选择你的 **固件版本**（列表视图）
3. 点「开始 Root」——App 自动：
   - 推送载荷到 `/data/local/tmp`
   - LD_PRELOAD 触发 CVE-2026-43499（物理内存读写 → KASLR 绕过 → SELinux 降级 → root UMH）
   - 成功后自动 KernelSU late-load
4. 看到「Root 成功」即完成，重启后需重新执行（不可持久化）

---

## 🛠 技术栈（简要）

- **漏洞**: CVE-2026-43499（pipe physrw 物理内存读写）
- **链**: pipe 原语 → KASLR slide 检测（tracefs / boot_id 双路径）→ SELinux 降级 → `call_usermodehelper_exec` 提权 → KernelSU 加载
- **内核**: 6.1.99（BYH7）/ 6.1.145（DZE2–DZG1）
- **平台**: e1q（S9210）/ e3q（S9280）

---

## ⚠️ 注意事项

- 每次重启后需要重新执行 Root（系统更新也会清除）
- 港版 DZG1（`S9280ZHS6DZG1`，构建 33419968）符号未知，若失败请提取该机型 boot 供定标
- 本工具仅供学习研究，请勿用于非法用途

---

## 📜 更新日志

```
v2.2 (2026-08-22)
  + 版本选择列表视图（RadioButton）
  + DZG1 专用载荷（修正 nfulnl_logger 偏移 0x16a6547）
  + BYH7 SELINUX enforcing 偏移修复（0x02420440 → 0x02420441）
  + 修复 release 构建收款码丢失（R8 收缩改为静态引用 + keep.xml）
  + RootFlow 页自动申请 Shizuku 权限（幂等）
  + 收款二维码（微信+支付宝）接入关于页
```
