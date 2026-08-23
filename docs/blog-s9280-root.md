---
title: "S24 Ultra 国行免解锁 Root 实践：基于 CVE-2026-43499 的临时提权方案"
date: 2026-08-17
categories: [Android, 安全研究]
tags: [S9280, CVE-2026-43499, KernelSU, 免解锁root]
---

# S24 Ultra 国行免解锁 Root 实践

> 本文记录 Samsung Galaxy S24 Ultra（SM-S9280 国行）在**不解锁 bootloader、不熔断 KNOX** 的前提下实现临时 Root 的完整过程：从方案选型、漏洞链原理，到适配内核常量时踩过的坑，以及最终落地为可一键运行的 Android App 的结果。

---

## 一、背景：为什么需要"免解锁" Root

Samsung 国行机型解锁 bootloader 的成本极高：解锁即熔断 KNOX e-fuse（不可逆），部分功能（Secure Folder、Samsung Health、Samsung Pay 等）永久失效；同时 Samsung 的 rev bit（SW REV）机制让降级/自定义内核变得极其困难——一旦官方新固件把 rev bit 熔到更高位，任何旧版本 bootloader 都无法刷回。

我的设备经历了一路"折腾":从 rev-4 的 One UI 8.0 开始，刷到 8.5 后 rev bit 被熔到 6，官方 bootloader 重新锁定，自定义内核全部被 AUTH 校验拦下。此时**唯一剩下的路径就是内核漏洞**——不碰 bootloader，直接在运行时提权。

## 二、方案选型：CVE-2026-43499

经过调研，最终选择 **CVE-2026-43499**：一个通过浏览器/应用域竞态实现的内核 RCE，参考实现在 Galaxy S24 系列（e3q 平台）上验证可用。核心链路：

1. **KASLR 滑动恢复**：通过 tracefs 侧信道恢复内核基址，定位符号
2. **管道内存准备**：利用 pipe 页重排 + mm 结构泄漏，获得可控的内核内存
3. **CFI/物理内存 oracle**：通过 fops 路由 + 管道页获得任意内核读写
4. **root 提权**：伪造 `call_usermodehelper` work 注入系统 workqueue，以 root 身份执行 payload
5. **KernelSU late-load**：以 root 加载 kernelsu.ko，由 KernelSU Manager 接管 root 管理与模块生态

![App 首页](01-home.png)
*图1 RootMyS9280 App 首页*

## 三、适配过程中踩过的坑

漏洞链本身是公开研究，真正的工程量在**适配具体固件**。以下是几个印象最深的坑：

### 1. kmalloc_caches 偏移：同平台不同构建也天差地别

e3q 平台的 S928U1（美版）与 S9280（国行）虽然同为 6.1.145 GKI 内核，但**构建号不同**（33419968 vs 3254743），`kmalloc_caches` 的偏移完全不同（0x176c6f8 vs 0x176cbb8）。市面上流传的"通用工具"很多直接套用美版偏移，导致在国行上 16/16 次全部失败在内存准备阶段。这是第一个需要修正的点。

### 2. F_SETPIPE_SZ EPERM：不是内核限制，是残留进程污染配额

exploit 在准备管道时需要把管道扩容到 32 slots，设备上却反复出现 `F_SETPIPE_SZ: Operation not permitted`。起初怀疑是 Samsung 内核加了限制，反汇编 `pipe_fcntl` 后确认**内核逻辑完全是标准的**（pipe-max-size 1MB、soft limit 16384 页都正常）。最终定位：之前失败的 exploit 尝试留下了一个 `cve43499-hold` 进程，握着 480 个已扩容的管道，把 uid 2000 的 `pipe_bufs` 配额占满了。`kill` 掉残留进程后，压力测试从 261 个管道失败恢复到 400/400 全通过。

![运行日志](02-log.png)
*图2 App 运行日志（粗略模式只显示总结性进度）*

### 3. worklist 竞态：注入 workqueue 时的"定时炸弹"

root 阶段把伪造 work 注入 `system_unbound` 池的 worklist 时，如果**显示驱动恰好同时入队真实 work**，exploit 对链表头部的覆盖会把真实 work 变成"孤儿"——它保持 PENDING 但链表已断，下一次 `cancel_work_sync`（比如屏幕刷新）就会触发 `__list_del_entry_valid` BUG 直接 panic。这正是多次"成功拿到 root 几分钟后才重启"的元凶。

修复方式：注入前**立即重查 worklist 仍为空**，若期间有真实 work 入队则回滚计数器、放弃本次尝试。加上运行期间熄屏（让显示驱动静默），这个崩溃源基本堵死。

### 4. KDP 与模块选择

Samsung 内核有 KDP/RKP/DEFEX 防御。普通 GKI 的 kernelsu.ko 在国行上可以加载并响应（version 32525），而官方参考（S928U1）使用了打了 Samsung KDP 补丁的变体。目前普通模块在国行上工作正常，kdp 变体作为备选。

## 四、使用流程

1. 安装并启动 Shizuku（无线/有线 ADB 授权）
2. 打开 App，点击"开始 Root"（建议熄屏运行）
3. 等待日志出现 `临时 root 已获得`
4. 安装 KernelSU Manager（v3.2.5），强制停止后重开，显示"工作中 \<LKM\> [越狱模式]"
5. 按需安装模块：Zygisk-Next → LSPosed → KnoxPatch（Secure Folder 等 KNOX 功能回归）

![KernelSU Manager 工作状态](05-ksu-manager.png)
*图3 KernelSU Manager 显示驱动已加载（越狱模式）*

![KernelSU 模块列表](06-ksu-modules.png)
*图4 模块列表：Zygisk-Next / LSPosed / KnoxPatch 等*

![关于页](03-about-top.png)
*图5 关于页：版本信息、KNOX 状态动态检测*

![源码与许可公示](04-about-license.png)
*图6 源码与许可公示：点击条目可打开仓库或查看许可全文*

## 五、注意事项

- **停在 DZF2，不要升级**：新固件会修复 CVE-2026-43499
- exploit 是概率性的：失败就多试几次，必要时重启手机（成功率随尝试累加）
- 运行期间建议熄屏；运行中卡死/重启属内核 exploit 的正常现象
- **每次重启手机后都需要重新运行一次 App**（bootloader 锁定，无法持久化内核模块）
- 本方案不熔断 KNOX：e-fuse 保持原样（App 设置页可实时检测 `ro.boot.warranty_bit`）

## 六、致谢

- [IonStack / NebuSec] 的 CVE-2026-43499 公开安全研究
- [Root-My-Galaxy](https://github.com/BuSung-dev/Root-My-Galaxy) 免解锁 root 参考工程
- [KernelSU](https://github.com/tiann/KernelSU) 及 Zygisk-Next / LSPosed / KnoxPatch 生态

## 七、源码

项目已开源（GPL-3.0）：[github.com/nanoturtle1145/root-my-s9280](https://github.com/nanoturtle1145/root-my-s9280)

> 免责声明：本文及项目仅用于安全研究与自有设备维护，使用内核漏洞存在系统崩溃、数据丢失风险，请勿用于非法用途。

---

> 返回 [文档中心](README.md) · [根 README](../README.md)
