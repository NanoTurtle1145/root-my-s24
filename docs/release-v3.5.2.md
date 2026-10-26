## 本版主题：SM-S928B 欧版 S24 Ultra 全线支持

v3.5.1 之后的累积更新以 **v3.5.2** 发布。核心是把 **SM-S928B（欧版 S24 Ultra，e3q）**
从「只能借用港版 S9280 载荷」变成**独立适配**：

- **One UI 9.0（EZIF）经真机验证转为正式支持**（不再需要打开「未经测试的载荷」）；
- 一次性补齐 **One UI 6.1 / 7.0 / 8.0 三代**欧版载荷（`tested = false`，待真机验证）。

> **发布渠道说明**：本版先以两个预发布增量上线，随后合并为第三个预发布——
> - `v3.5.2-beta.205`：含欧版三代新载荷，EZIF 尚未转正
> - `v3.5.2-alpha.206`：EZIF 转正，但不含三代新载荷
> - `v3.5.2-beta.208`：**两者并集**
> - `v3.5.2-beta.211`：修复欧版三条载荷的真机失败（`MM_STRUCT_SZ`）
> - `v3.5.2-beta.219`：**当前推荐**，在 211 基础上新增 SM-S9210 港版 CZD2 支持（issue #20）
>
> 早期预发布保留作对照；后续稳定版会一次性包含全部内容。

---

## 一、SM-S928B 欧版 One UI 9.0（EZIF）转为已实测

- 载荷 `cve-2026-43499-S928BXXU6EZIF`（One UI 9.0 / 内核 `6.1.162-android14-11-34343818-abS928BXXU6EZIF`，构建号 34343818）
- 专属 KernelSU 3.3.0（32601）驱动 `ksud-ezif-32601`
- **2026-10-23 由欧版真机实测通过** → 条目 `tested = true`：
  不再需要先在设置里打开「未经测试的载荷」才能看到，也不再显示风险提示

## 二、新增欧版三代载荷（AXK5 / BYD9 / CZA1，待真机验证）

| 载荷资产 | 系统 | 内核版本 | 构建号 |
|---|---|---|---|
| `cve-2026-43499-S928BXXU4AXK5` | One UI 6.1 / Android 14 | `6.1.75-android14-11-29543898` | 29543898 |
| `cve-2026-43499-S928BXXU4BYD9` | One UI 7.0 / Android 15 | `6.1.99-android14-11-30958380` | 30958380 |
| `cve-2026-43499-S928BXXS4CZA1` | One UI 8.0 / Android 16 | `6.1.128-android14-11-31999054` | 31999054 |

- 三条均为 `e3q` 平台、`EUR` CSC、Samsung 签名 `SM-S928B_EUR_XX_QKEY2`
- 驱动沿用通用 6.1 系列 `ksud-selected`（运行时按内核 vermagic 自动替换，未构建专属驱动）
- **均为 `tested = false`**：需在设置中打开「未经测试的载荷」才会显示，页面上会带风险提示
- ⚠ **同构建号 ≠ 同机型**：欧版 CZA1 与港版 CZA1 构建号同为 `31999054`，但
  `SLIDE_NFULNL_LOGGER_OFF` 相差 8 字节（欧版 `0x0160b70d` vs 港版 `0x0160b705`），
  `.rodata` 布局不同，**两个载荷不可互换**

定标流程见 [`docs/s928b-eur-axk5-byd9-cza1-target-complete.md`](s928b-eur-axk5-byd9-cza1-target-complete.md)：
全自动符号身份迁移 + 镜像实测 + BTF 核对，三个目标均 **0 项未解析**、P0 指纹校验通过。

## 三、root 流程改进

- **late-load 前可禁用 KernelSU 模块**：流程中新增「先禁用 KSU 模块」步骤，并明确说明
  该操作**不会自动恢复**，需手动重新启用
- **修复 root helper 的调用方式**
- 步骤编号修正

## 四、未包含 / 待验证（重要）

- 上述**欧版三代载荷尚无真机实测反馈**，因此不标记为「已实测」。验证通过后会按惯例转为
  `tested = true`；若失败请附 `getprop ro.build.fingerprint` 与运行日志反馈。
- **美版 S24+（SM-S926U1 / XAA）DZE2 载荷**仍不包含（与 v3.5.1 相同，等真机验证后再放）。

## 五、真机反馈修复（`v3.5.2-beta.211`）

欧版用户实测 **A14（AXK5）与 A15（BYD9）失败**，A16（CZA1）未测。日志显示 KASLR slide 正常
（`slide-kaslr-ok source=tracefs slide=0xc0000`），但 physrw 阶段 100% 失败：

```
[-] KernelSnitch mm_struct leak failed
[-] prepare_kernel_page did not find usable nonzero source pointers
[!] pipe KernelSnitch sk_buff page leak failed
```

**根因**：`MM_STRUCT_SZ` 不是 BTF 的 `sizeof(mm_struct)`（6.1 全系 `0x3c0`），而是 KernelSnitch
bruteforce 的**步长**（`mm_struct_candidate += MM_STRUCT_SZ`）与 slab 对象数分母
（`objs_per_slab = ORDER3_SIZE / MM_STRUCT_SZ`）。步长写成 `0x3c0` 会跨过真实的 mm_struct，
泄漏必定失败。三条载荷沿用了未验证模板（CZA1）里的值，而**同机型已实测的 EZIF** 及其余所有
已验证 6.1 载荷都用 `0x400`。

**修复**：三条载荷 `MM_STRUCT_SZ → 0x400` 重编（均 104,128 B），本版起生效。
三条仍为 `tested = false`，待真机确认后再转正。
详见 [`s928b-eur-axk5-byd9-cza1-target-complete.md`](s928b-eur-axk5-byd9-cza1-target-complete.md) §8。

## 系统要求

- 机型：见根 README「支持设备」（S24 系列、S23/S25 部分机型、Z Flip/Fold 系列与 W23）
- 系统：Android 12 及以上（Samsung One UI，具体固件见支持列表）
- 授权：Shizuku 或无线调试直连
- 管理器：按机型选择 KernelSU 3.3.0（32601，推荐）或 3.2.5（32525，回退）

## 已知问题

- 欧版三代新载荷为**未经测试**状态，真机结果待收集；遇到 kernel panic 属预期风险范围，请先备份数据再试
- BakaSU 模式为实验性：每次开机需重跑流程，`selinux_hide` 不可用，且**不要**点管理器内
  「安装/刷入」页面（会写 boot，有变砖风险）
- 部分第三方移植载荷（S23/S25/S26/折叠屏系列等）仍标记为「未经测试」，
  需在设置中打开「未经测试的载荷」才显示

## 六、新增 SM-S9210 港版 CZD2（`v3.5.2-beta.219`，issue #20）

用户提供原厂 boot 镜像后独立定标：

- 载荷 `cve-2026-43499-S9210ZHS5CZD2`（104,128 B）
- 内核 `6.1.128-android14-11-31999054-abS9210ZHS5CZD2`（构建号 31999054）
- 条目 `S9210CZD2`：One UI 8.0 / 港版 Galaxy S24 / 港台分组，`tested = false`

**同构建号 ≠ 同平台**：它与 e3q 的 CZA1 内核构建号完全相同，但 **e1q 是 Exynos 2400、
e3q 是骁龙 8 Gen 3**，90 个同名偏移里 25 处不同（text 区恒定 Δ+0x800、ashmem 函数区
Δ+0xa54），**不可互换载荷**。定标模板因此取同平台的 `e1q-S9210ZCU4BYH7`。

定标摘要：迁移 19 / 镜像派生 6 / 沿用 114（BTF 实测 47）/ P0 指纹 ok；
另手工修正了 `SELINUX_ENFORCING_OFF`（BTF 实测 `selinux_state.enforcing` 偏移为 0，
模板中未经真机验证的 +1 是错的）。
详见研究仓库 `devices_ports/s9210_czd2_analysis.md`。

## 产物校验

| 版本 | APK | 大小 | SHA-256 |
|---|---|---|---|
| `v3.5.2-beta.219` | `RootMyS24-v3.5.2-beta.219.apk` | 50,691,463 B | `4da607f513152dbd7fc5605425ea9e6b9c16cf98a2e1861d8fb9adca30d48ecb` |
| `v3.5.2-beta.211` | `RootMyS24-v3.5.2-beta.211.apk` | 51,155,942 B | `235723d7c9a2247b74a3212981181821141b68f7da4629a4a3117b76fe9455c8` |
| `v3.5.2-beta.208` | `RootMyS24-v3.5.2-beta.208.apk` | 51,153,078 B | `3d5214c95a0e201a37ab95326253065cb4c9e4290d611421ce70879a3e477c73` |
| `v3.5.2-beta.205` | `RootMyS24-v3.5.2-beta.205.apk` | 51,222,070 B | `77d227dbbd7a7b6c9fd205ab42b50084ddb16140dbf2ad7884d664c1b0e6aa54` |
| `v3.5.2-alpha.206` | `RootMyS24-v3.5.2-alpha.206.apk` | 51,116,963 B | `1ab98cad3470dbb3ce332ce6f27e66e768132c816a7fec0d5c5f0493f402123b` |

- 下载：<https://github.com/NanoTurtle1145/root-my-s24/releases>
- 两个 APK 均使用官方 keystore 签名（证书 SHA-256
  `12daafaaa2beea11c6ec6c6353d4899331b0fbe15e17c20c887e0a4510ade9af`）
