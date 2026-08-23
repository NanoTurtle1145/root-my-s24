# RootMyS24 技术原理

> 面向用户与开发者的技术背书：这个 App 为什么能免解锁 root、每一步在做什么、有什么风险。
> 完整研究笔记（漏洞成因、利用链源码对照、防护绕过细节）见研究仓库
> [VULNERABILITY_ANALYSIS.md](https://github.com/NanoTurtle1145/samsung-root-research/blob/main/VULNERABILITY_ANALYSIS.md)。

## 一句话原理

利用内核漏洞 **CVE-2026-43499**（rtmutex 的内核栈 use-after-free）获得任意物理内存读写，依次完成 KASLR 绕过、SELinux 降级、以 root 执行辅助程序，最后加载 KernelSU 驱动实现临时 root。全程不碰 bootloader，不烧 KNOX e-fuse。

## 漏洞是什么

`kernel/locking/rtmutex.c` 的 `remove_waiter()` 有一条错误清理路径：本应清掉真正等待者的 `pi_blocked_on` 反向指针，却误清了当前执行者。结果等待者的 `task_struct` 上残留一个指向**已释放内核栈**的悬垂指针——内核栈 UAF。

触发方式：三个线程用 `FUTEX_CMP_REQUEUE_PI` 构造一条优先级继承链的死锁回滚，迫使内核走入错误路径。

## App 点下「开始 Root」后发生了什么

```
[1] 推送载荷        exploit/root helper/ksud 三件套写入 /data/local/tmp
[2] 触发漏洞        LD_PRELOAD 注入 /system/bin/sh，三线程构造 PI 链死锁回滚
[3] 栈复用          pselect6 复用被释放的内核栈，栈上伪造 rt_mutex_waiter
[4] KASLR 恢复      tracefs 事件泄露内核地址（或物理指纹扫描兜底）
[5] CFI 改表        改写 ashmem 的 fops 函数指针 → 获得稳定内核读写原语
[6] SELinux 降级    物理写清零 selinux_state.enforcing
[7] root 执行       workqueue 注入 call_usermodehelper → root 运行辅助程序
[8] KernelSU 加载   ksud late-load 把 KernelSU 驱动加载进内核
```

## 为什么不需要解锁 bootloader

解锁 BL 需要烧写设备、改变信任根状态；本项目走的是**运行时漏洞利用**：内核每次开机都一样，漏洞窗口只在开机后一段时间内开放（boot quiet window），过了窗口重新开机即可。因此 bootloader 保持锁定、KNOX e-fuse 保持 0、`verifiedbootstate` 保持 green。

## 为什么 root 是临时的

漏洞利用不修改任何持久化分区——内核、system、vendor 分区的字节都没变。KernelSU 驱动是运行时注入（late-load），重启后消失。这也是安全研究的目的：每次重启后跑一次 App 重新加载，设备随时可以回到"无 root 原状"。

## 风险提示

- **概率性成功**：漏洞窗口是竞态，可能需要多次尝试（App 默认自动重试 30 轮）
- **崩溃风险**：内核级竞态可能触发 panic 重启（日志已做实时落盘，重启后自动恢复）
- **不升系统**：新固件会修复该漏洞，升级后本工具失效
- 仅供学习研究与自有设备维护，请勿用于非法用途

## 各阶段对应源码

| 阶段 | 源码（研究仓库 `02_exploit工程/fusion-s24u/src/`） |
|---|---|
| 载荷监督与重试 | `preload.c` |
| 物理读写原语 / cache gate | `pipe.c` |
| pselect 栈复用 / CFI 改表 | `fops.c` |
| KASLR slide 恢复 | `slide.c` |
| SELinux 降级 / workqueue 注入 / root 执行 | `root.c` |
| root 守护进程 | `su_daemon.c` |
| 各固件符号偏移 | `targets/<目标>/target.h` |

## 进一步阅读

- 研究仓库：[samsung-root-research](https://github.com/NanoTurtle1145/samsung-root-research)
  - [VULNERABILITY_ANALYSIS.md](https://github.com/NanoTurtle1145/samsung-root-research/blob/main/VULNERABILITY_ANALYSIS.md)（漏洞原理剖析）
  - [RUN_LOG_ANALYSIS.md](https://github.com/NanoTurtle1145/samsung-root-research/blob/main/RUN_LOG_ANALYSIS.md)（成功/失败日志判读）
- 漏洞研究源：[CVE-2026-43499](https://github.com/IonStack/CVE-2026-43499)（IonStack / NebuSec）

---

> 返回 [文档中心](README.md) · [根 README](../README.md)
