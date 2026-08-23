# SM-S9380 + Root-My-Galaxy 临时 Root 经验（酷安文章归档）

> 归档日期：2026-08-22
> 来源：酷安用户分享（SM-S9380 国行 ZCSCCZG1 / One UI 8.5 测试记录，附"受虐滑稽"调侃语气）
> 价值：包含 **BYH7 失败日志的直接对照证据**（struct page 偏移错误特征），以及 boot quiet window 时序参数、Shell/App 域差异等实战结论。

---

## 〇、结论

漏洞能否成功几乎不取决于运气，而取决于一个时序参数（boot quiet window，见第五节）。

## 一、漏洞原理（CVE-2026-43499 GhostLock）

内核 rtmutex 的 use-after-free，位置在 `kernel/locking/rtmutex.c` 的 `remove_waiter()`：

- 正常逻辑：移除等待者时清理 `waiter->task->pi_blocked_on` 反向指针
- 错误路径：误用 `current`（当前任务）而不是 `waiter->task`（真正的等待者）
- 结果：真正 waiter 任务身上残留指向**已释放内核栈**的悬垂 `pi_blocked_on` → 内核栈 UAF

构造方式：三线程（waiter/owner/consumer）构造 PI futex 链，用 `FUTEX_CMP_REQUEUE_PI` 制造死锁回滚触发错误清理路径。

## 二、完整利用链

```
死锁回滚   FUTEX_CMP_REQUEUE_PI 制造 PI 链死锁 → 触发 remove_waiter 错误路径
           → 释放内核栈但留下悬垂 pi_blocked_on
    ↓
栈复用     pselect6 复用刚释放的内核栈
    ↓
伪造 waiter 在复用栈上伪造 rt_mutex_waiter 结构，悬垂指针指向可控数据
    ↓
P0 管道 oracle   用 pipe 页做页级物理内存读写原语
    ↓
泄露 KASLR slide  通过 P0 oracle 读物理内存算出内核随机化基址
    ↓
CFI 改表          改写 misc_fops 等 fops 表的函数指针
    ↓
FOPS 物理读写      更稳定的基于 pipe 的物理读写
    ↓
KernelSU late-load 加载 KernelSU 进内核，临时提权
```

难点集中在第四（P0 oracle）、五（KASLR）、七（FOPS cache gate）步的"命中"。

## 三、固件 profile 移植（SM-S9380 国行 ZCSCCZG1）

官方仓库 `BuSung-dev/Root-My-Galaxy` 只有韩版 `pa3q-S938NKSUACZF1` 和港版 `pa3q-S9380ZHUBCZF1`，**没有国行 ZCSCCZG1**。7 月安全补丁动了内核指令，直接套用 6 月 profile 会失败。

移植流程：解包 `boot.img.lz4` → 提取 vmlinux/kallsyms → BTF 反推结构体偏移 → 对照旧固件 profile 逐个修正。

**ZG1 最终确认的关键偏移**（直接影响 FOPS 物理读写的 slab_cache 命中）：

```
STRUCT_PAGE_SIZE              = 0x40
STRUCT_PAGE_COMPOUND_HEAD_OFF = 0x08
STRUCT_SLAB_CACHE_OFF         = 0x08
STRUCT_PAGE_TYPE_OFF          = 0x30
```

> 这四项错了就是一堆 `dead000000000100` 毒化值。

## 四、坑：Shell 域能跑通 ≠ App 域能跑通

提权逻辑在 Shell 域（adb 直接跑二进制）能通，塞进 App 就失败。原因：

- SELinux 上下文不同
- cgroup 归属不同（影响 kmalloc 落到 cgroup 缓存还是 normal 缓存——与 FOPS 的 `kmalloc_cgroup_2k_cache` 匹配相关）
- seccomp 过滤差异

> 移植时必须把 exploit 完整地在 App 域验证一遍，尤其是缓存归属步骤。

## 五、关键参数：boot quiet window（时序）

- 写窗口概念：pselect 返回 `ret > 0` 写窗口打开；`ret == 0` 写窗口关闭
  - `slide pselect returned ... ret=8` → 能写物理内存
  - `slide pselect returned ... ret=0` → 后续全废
- 决定写窗口的是开机后等待时长，由 `preload.c` 的 `APP_MIN_BOOT_UPTIME_SEC` 控制
  - 开机 60 秒启动：boot 分配器活跃期，写窗口打不开（ret=0，write status=256 ok=0），**必失败**
  - 开机 120 秒启动：quiet window，写窗口稳定打开（ret=8，write status=0 ok=1），基本一轮秒杀
- 实测：120→60 改小后反复重启 5 小时一次不成；换回 120 重启一次直接成功

> **我们的 fusion-s24u `src/preload.c` 已使用 `APP_MIN_BOOT_UPTIME_SEC = 120`，与文章结论一致，无需修改。**

## 六、两个「门」

### P0 oracle gate
扫描 pipe 页找两个 marker：写入 marker `RMG-P0-PIPE`、校验 marker `RMG-P0-ORACLE-GATE`（payload 偏移 `P0_ORACLE_GATE_PAGE_OFF 0xe80`）。理想命中 `gate_hits==1 && changed_pages==0` → 读出 KASLR slide（日志：`slide-kaslr-ok source=physical slide=...`）。命中率波动，但保证 120 秒窗口后整体稳定。

### FOPS cache gate
从 reclaim 的 pipe 页找出 slab_cache 匹配的目标缓存：读 struct page 偏移 `STRUCT_SLAB_CACHE_OFF` 处的 slab_cache 指针，与 `kmalloc_pipe_cache` 比对。本机目标缓存 `0xffffff8001cf4c00`（2k cgroup/kmalloc 缓存），命中日志 `cache08=ffffff8001cf4c00 match=1`。

**失败两种典型**：

| cache08 值 | 含义 | 处理 |
|---|---|---|
| `dead000000000100` | SLUB 毒化值，页被 poison | 回收状态不对（时序窗口问题） |
| `fffffffe24997a08` | 读到 vmemmap 地址 | **struct page 偏移算错**（profile 移植问题） |

> **BYH7 对照**：`BYH7_e1q_fail.txt` 中 `cache08=fffffffe208aac08`、`cache10=fffffffe2919de08` 均以 `fffffffe` 开头 = **vmemmap 地址特征 → struct page 偏移错误**。与 S9280 成功日志 `cache08=0000000000000000` + `cache18=ffffff8001cf6800`（正常内核地址）对比，可确认 BYH7（6.1.99）的 struct page 布局与 DZF2（6.1.145）不同，需按文章第三节四项偏移逐项核对。

## 七、稳定参数基线（preload.c / 环境变量）

```
APP_MIN_BOOT_UPTIME_SEC      = 120   开机后等待 120 秒再提权（基础，别改小）
EXPLOIT_ATTEMPTS             = 24    总尝试次数
P0_ATTEMPT_TIMEOUT_SEC       = 45    P0 阶段单次超时
EXPLOIT_ATTEMPT_TIMEOUT_SEC  = 120   单次 exploit 超时
PSELECT_DELAY_USEC           = 20000 基础延迟
```

- 重试预算无需堆大（尝试过的 192/90/240 无益），24/45/120 足够，堆大反而掩盖时序问题
- 提高命中率正确方向：保证 120 秒窗口 + 干净开机环境（关电池优化、飞行模式、减少后台），而不是改内核延迟常量

---

## 与我们项目的关联

1. **BYH7 根因修正**：`RUN_LOG_ANALYSIS.md` 中 BYH7 失败从"pipe_buffer 布局差异"精确为 **`struct page` 字段偏移错误**（vmemmap 地址特征 `fffffffe` 前缀），需按 `STRUCT_PAGE_SIZE=0x40` 等四项在 6.1.99 上核对。
2. **App 域验证**：BYH7 测试走 App（Shizuku shell 域），文章提示 cgroup 归属影响缓存匹配——BYH7 的 `selected=0xffffff8001cf7100`（cgroup2k）与目标缓存的匹配也可能受 App 域影响，除偏移外还需注意运行环境。
3. **时序参数**：fusion-s24u 已是 `APP_MIN_BOOT_UPTIME_SEC=120`，与文章一致；App 侧 `EXPLOIT_ATTEMPTS=30` 略高于文章的 24，无碍。
4. **新 CVE-2026-64560**：文章作者正在尝试（触发内核崩溃），尚未有稳定结论，持续关注。

---

## 致谢（原文）

- BuSung-dev / Root-My-Galaxy —— exploit 原作者与 payload 仓库
- NebuSec / CyberMeowfia —— CVE-2026-43499 漏洞发现

---

> 返回 [文档中心](README.md) · [根 README](../README.md)
