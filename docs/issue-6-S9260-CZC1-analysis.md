# Issue #6 分析：SM-S9260（S9260ZHS5CZC1）套用 S9280 CZA1 载荷失败

> 报告人：@pokemc32ryan ｜ 2026-09-08 ｜ App 版本 v3.1.0 (build 126)
> 附件日志：`rootmys9280-SM-S9260.txt`（37,256 B）、`rootmys9280-SM-S9260.1.txt`（27,357 B）

## 结论（TL;DR）

**不是「概率性失败」，而是载荷与内核构建不匹配导致的确定性失败。** 6 轮运行 × 30 次尝试 = **180 次全部**卡在链路第一步（P0 物理页泄漏），没有一次进入后续阶段。

用户设备是**外版** `S9260ZHS5CZC1`（内核构建号 31999054），而他套用的是**另一个构建**的载荷（港版 `S9280ZHS4CZA1`）。该构建号目前没有任何载荷，所以怎么重试都一样。

## 先明确载荷的通用规则（这条最容易被误解）

S24 全系列（SM-S9210 / SM-S9260 / SM-S9280，即 e1q / e2q / e3q）软件通用，载荷只分两套：

| 分支 | 适用范围 | 说明 |
| --- | --- | --- |
| **国行** | 国行 S24 全系 | 与国行固件一起定标 |
| **外版** | 港版 / 台版 / 欧美 S24 全系 | **外版之间可以互串**，不分机型与地区 |

也就是说，载荷是按**内核镜像（构建）**定标的，不是按机型定标的：**同一构建号的固件，跨机型、跨地区都能用**。真正不能混的是「国行 ↔ 外版」——例如同为 DZF2，国行 `kmalloc_caches=0x0176cbb8`、港版 `0x0176c6f8`。

> 反过来说：**只要构建号对得上，S9260 用 S9280 的载荷没有任何问题。** issue #3 就是例子——台版 SM-S9260 DZG1 用港台 DZE2 载荷成功，因为两者是同一构建。

## 设备与载荷事实

| 项 | 值 |
| --- | --- |
| 机型 | SM-S9260（S24+，外版） |
| 固件 | `S9260ZHS5CZC1` |
| 内核构建 | `6.1.128-android14-11-31999054-abS9260ZHS5CZC1` |
| 系统 | Android 16（BP2A.250605.031.A3），安全补丁 2026-03-05 |
| KNOX | warranty_bit=0、verifiedbootstate=green（未熔断） |
| 所加载荷 | `cve-2026-43499-cza1`（104128 B），label = `e3q-S9280ZHS4CZA1-app-physical-p0-oracle` |
| 载荷所属构建 | 港版 **CZA1**（`kmalloc_caches 0x16bad78`、`nfnetlink_log` 0x0160b705） |

**构建不匹配**：设备是 CZC1（构建 31999054），载荷是 CZA1——不是同一个内核镜像。

## 失败证据（全部来自附件日志）

每一轮运行的形态完全一致：

```
[+] build config pid=… label=e3q-S9280ZHS4CZA1-app-physical-p0-oracle slide=pselect main=pselect
[+] p0 profile pid=… slide_logger=ffffff802960b705 bootid_data=… init_task=… root_tg=… sysctl_bootid=…
[-] slide tracefs pre-oracle unavailable; using physical scan
[!] pipe KernelSnitch sk_buff page leak failed
[!] pipe page child did not report base
[-] exploit attempt=N/30 failed status=255
```

| 指标 | 数值 |
| --- | --- |
| `exploit attempt=` 行 | 65（6 轮 × 30） |
| `pipe KernelSnitch sk_buff page leak failed` | 27 |
| `pipe page child did not report base` | 27 |
| 出现 `triggered=1` / `verified=1` / `root=1` / `retval=0 socket=1` / `completed` | **0 次** |

`slide_logger=ffffff802960b705`（内核基址 `ffffff8028000000` + `0x0160b705`）正是 **CZA1 镜像**的 `nfnetlink_log` 偏移——日志本身证明了用的是 CZA1 那套定标。

## 为什么必然失败（而不是「多试几次」）

漏洞链第一步要用 pipe 把页泄漏出来、拿到 P0 物理基址（就是那两行 `failed`）。这取决于**编进载荷的编译期定标**：`kmalloc_caches`、`struct page`/slab 字段位置、`nfnetlink_log` 偏移等——这些值只对**定标时用的那个内核镜像**成立。

CZC1（构建 31999054）与 CZA1 是两个不同的镜像，偏移不同 → oracle 永远拿不到 base → **每次都以同样方式失败**。这正是日志呈现的 180/180 形态。

日志里还有 `[-] slide tracefs pre-oracle unavailable`：tracefs 预判拿不到 slide，退回物理扫描；而物理扫描的正确性完全依赖上面的偏移，所以无法自救。

## 附带的 bug：「关屏会掉 Shizuku」

用户反馈关闭屏幕后 Shizuku 掉线。根流程**会主动熄屏**（日志里的 `◆ 螢幕已關閉`）以降低内核竞态概率；日志中也能看到状态交替：

```
[1/5] 檢查 Shizuku...  ✔ Shizuku 已就緒
[1/5] 檢查 Shizuku...  ✗ 失敗: Shizuku 未在執行
```

定位方向（按可能性排序）：

1. 通过**无线调试**拉起 Shizuku 时，它依附于那次的 ADB 会话；熄屏/会话回收会让它一起退出
2. 系统在熄屏后**冻结后台进程**，Shizuku 的 binder 变得不可用，表现为「未在執行」
3. 应用自身被冻结，导致检查用的 binder 调用失败（每次检查都新建连接，更容易撞上）

处理方向：熄屏前确认 Shizuku 存活；运行中若检测到它消失，**不要直接判失败**，而是提示并提供「改用无线调试直连」的路径（本 App 自带该通道，不依赖 Shizuku）。

## App 侧待改进（已记录）

1. **条目表述按规则来**：现在写「SM-S9280 港版/台版」会让人以为只适配某个机型，应改为「**S24 全系 · 外版**」／「**S24 全系 · 国行**」，与上面的通用规则一致
2. **按内核构建匹配载荷**：选中条目的构建号与当前固件不符时给出明确提示（本例是 CZC1 却选了 CZA1），而不是让用户试 180 次
3. **失败信息不要暴露 Java 异常**：build 126 日志中出现过
   `✗ 失敗: Attempt to invoke virtual method 'java.lang.Class java.lang.Object.getClass()' on a null object reference`
   这是应用侧空指针，应转成可读提示
4. **Shizuku 掉线**：见上一节，检测到消失时应引导切换授权方式

## 后续（要真正支持这个固件）

需要 **CZC1 这个构建**（外版，内核 `6.1.128-…-31999054`）的定标：

1. 取得该固件的内核镜像（`AP_S9260ZHS5CZC1_*.tar.md5` 中的 kernel；同一构建在 S9210/S9280 上通用）
2. 用它重新定标：生成该构建的 `target.h` 偏移与 `p0_fingerprint.h` 指纹表
3. 真机验证整链（KASLR slide → CFI fops → pipe 物理读写 → KernelSU late-load → 授予 su）
4. 定标成果按「外版」分支回馈 [Root-My-Galaxy-Payloads](https://github.com/BuSung-dev/Root-My-Galaxy-Payloads)，供所有外版 S24 共用

在该构建的载荷出现之前，这个固件没有可用载荷，重试与重启都不会改变结果。
