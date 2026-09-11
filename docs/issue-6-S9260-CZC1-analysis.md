# Issue #6 分析：SM-S9260（S9260ZHS5CZC1）套用 S9280 CZA1 载荷失败

> 报告人：@pokemc32ryan ｜ 2026-09-08 ｜ App 版本 v3.1.0 (build 126)
> 附件日志：`rootmys9280-SM-S9260.txt`（37,256 B，159 行）、`rootmys9280-SM-S9260 (1).txt`（27,357 B）

## 结论（TL;DR）

**不是「概率性失败」，而是目标档不匹配导致的确定性失败。** 6 轮运行 × 30 次尝试 = **180 次全部**卡在链路第一步（P0 物理页泄漏），没有一次进入后续阶段。

用户选中的是 **S9280（e3q）港版/台版 CZA1** 载荷，而设备是 **SM-S9260（e2q）**、内核构建号也完全不同。仅「内核版本都是 6.1.128」并不构成可通用条件。

## 设备与载荷事实

| 项 | 值 |
| --- | --- |
| 机型 | SM-S9260（S24+，代号 **e2q**，指纹 `samsung/e2qzhx/e2q:16/...`） |
| 固件 | `S9260ZHS5CZC1` |
| 系统 | Android 16（BP2A.250605.031.A3），安全补丁 2026-03-05 |
| 内核 | `6.1.128-android14-11-31999054-abS9260ZHS5CZC1` |
| KNOX | warranty_bit=0、verifiedbootstate=green（未熔断） |
| 所加载荷 | `cve-2026-43499-cza1`（104128 B），label = **`e3q-S9280ZHS4CZA1-app-physical-p0-oracle`** |
| 载荷定标 | 固定档，`kmalloc_caches 0x16bad78`（= 港版 S9280 CZA1 的定标值） |

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

统计：

| 指标 | 数值 |
| --- | --- |
| `exploit attempt=` 行 | 65 |
| `pipe KernelSnitch sk_buff page leak failed` | 27 |
| `pipe page child did not report base` | 27 |
| 出现 `triggered=1` / `verified=1` / `root=1` / `retval=0 socket=1` / `completed` | **0 次** |

两点关键：

1. **`slide_logger=ffffff802960b705`** —— 内核基址 `ffffff8028000000` + `0x0160b705`，正是**港版 S9280 CZA1** 的 `SLIDE_NFULNL_LOGGER_OFF`。日志自己证明了载荷用的是 e3q（S9280）定标。
2. **`[-] slide tracefs pre-oracle unavailable`** —— tracefs 预判不可用（未能从 tracefs 读到 slide），退回物理扫描；而物理扫描的正确性完全依赖目标内核的 `struct page` / slab 偏移。

## 为什么必然失败（而不是「多试几次」）

漏洞链的第一步是用 pipe 把页泄漏出来、拿到 P0 物理基址（`pipe KernelSnitch sk_buff page leak failed` → `pipe page child did not report base` 就是这一步失败）。

这一步依赖的偏移是**编进载荷**的编译期定标：

- `struct page` 上 `slab_cache` 之类字段的位置
- `kmalloc_caches`（本载荷写死 `0x16bad78`）
- `nfnetlink_log` 符号位置（slide 预判用）
- 内核物理加载基址相关常量

设备实际内核是 `6.1.128-…-31999054-abS9260ZHS5CZC1`，与 e3q CZA1 的镜像**不是同一份构建**：机型不同（e2q vs e3q）、构建号不同。偏移一旦不匹配，oracle 拿不到 base，**每次都会以同样方式失败**——这正是日志呈现的 180/180 形态。

## 与 issue #3 的关系（为什么上次台版 S9260 却成功了）

issue #3 里台版 **SM-S9260 DZG1** 用港台 **DZE2** 载荷成功，容易被误读成「S9260 可以套用 S9280 载荷」。实际那是**恰好同构建号**（DZG1 与港版 DZE2 内核镜像一致）的巧合，不是机型级通用规则。本 issue 的 `CZC1` 构建号没有对应载荷，所以必然失败。

> 反例同样存在：国行与港版同为 DZF2 时 `kmalloc_caches` 也不同（`0x0176cbb8` vs `0x0176c6f8`），跨地区不能混用。

## 附带的 bug：「关屏会掉 Shizuku」

用户反馈关闭屏幕后 Shizuku 掉线。根流程**会主动熄屏**（日志里的 `◆ 螢幕已關閉`），以降低内核竞态概率；日志中也能看到交替出现：

```
[1/5] 檢查 Shizuku...
✔ Shizuku 已就緒
…
[1/5] 檢查 Shizuku...
✗ 失敗: Shizuku 未在執行。請先啟動 Shizuku!
```

定位方向（按可能性排序）：

1. 通过**无线调试**拉起 Shizuku 时，它依附于那次的 ADB 会话；熄屏/ADB 会话回收会让它一起退出
2. 一加/三星等系统的**后台冻结**：熄屏后进程进入 cached/frozen 状态，Shizuku 的 binder 变得不可用，表现就是「未在執行」
3. 应用自身被冻结，导致检查用的 binder 调用失败（每次检查都新建连接，更容易撞上）

处理建议：熄屏前确认 Shizuku 存活；运行中若检测到它消失，**不要直接判失败**，而是提示并提供「改用无线调试直连」的路径（本 App 自带该通道，不依赖 Shizuku）；或引导用户用支持开机自启/后台常驻的方式启动 Shizuku。

## App 侧待改进（已记录）

1. **选载荷时做机型校验**：用 `ro.product.device`/`ro.product.model` 与条目声明的适配机型比对，不符就明确警示（现在只按地区分组展示，S9260 用户可以直接选中写着 S9280 的条目）
2. **失败信息不应暴露 Java 异常**：build 126 的日志里出现过
   `✗ 失敗: Attempt to invoke virtual method 'java.lang.Class java.lang.Object.getClass()' on a null object reference`
   这是应用侧的空指针，应转成「载荷与机型不匹配」这类可读提示
3. **跨机型套用的可预期性**：同地区同构建号可共用、跨构建号/跨机型不可套用——应在 UI 上把这条规则讲清楚，而不是让用户靠试

## 后续（需要什么才能真正修好）

要支持 `S9260ZHS5CZC1`（e2q / kernel 6.1.128 / build 31999054），需要：

1. 该固件的官方内核镜像（`AP_S9260ZHS5CZC1_*.tar.md5` 里的 kernel）
2. 用它重新定标：生成该目标的 `target.h` 偏移与 `p0_fingerprint.h` 指纹表
3. 真机验证整链（KASLR slide → CFI fops → pipe 物理读写 → KernelSU late-load → 授予 su）
4. 定标成果回馈 [Root-My-Galaxy-Payloads](https://github.com/BuSung-dev/Root-My-Galaxy-Payloads)，供社区共用

在此之前，该机型**没有任何可用载荷**，重试与重启都不会改变结果。
