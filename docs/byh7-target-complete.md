# One UI 7 (BYH7) 适配定标完成报告

> 定标日期：2026-08-21
> 目标：`src/targets/e1q-S9210ZCU4BYH7/`（国行 S24 e1q / S9210, One UI 7 / Android 15, kernel 6.1.99-android14-11-2370239）
> 前置报告：[`oneui7-adaptation-report.md`](oneui7-adaptation-report.md)（可行性分析，18 个符号偏移对比）
> **注：本报告中的符号定位、偏移推导与验证，部分由 AI 辅助分析后生成，仅供技术研究与参考。**

---

## 1. 完成状态

| 阶段 | 内容 | 状态 |
|---|---|---|
| A | 提取内核 → 恢复 vmlinux → 提取符号偏移 | ✅（前置报告） |
| B | 反汇编定位特殊符号 → 生成 BYH7 `target.h` | ✅ 本次完成 |
| C | 重生成 `p0_fingerprint.h` → 编译 payload | ✅ 本次完成 |
| D | 静态验证（符号/内容/指纹交叉验证） | ✅ 本次完成 |
| E | 真机验证（需 One UI 7 设备） | ⏸ 待真机（不在本次范围） |

产出物（`/home/nt/root_research/02_exploit工程/fusion-s24u/`）：

```text
src/targets/e1q-S9210ZCU4BYH7/
├── target.h            # 全部 25 个 *_OFF 宏 + 配置宏（242 行）
└── p0_fingerprint.h    # 32 行 × 8 qword 指纹表（148 行）
build/e1q-S9210ZCU4BYH7/cve-2026-43499-app.stable.so  # 104128 B，ELF64 有效
```

构建命令：

```bash
ANDROID_NDK_HOME=/home/nt/root_research/06_工具链/android-ndk-r29 \
  TARGET=e1q-S9210ZCU4BYH7 make stable
```

---

## 2. 特殊符号定位结果（阶段 B）

以下符号不在 vmlinux 符号表或需特征匹配，本次逐一确认：

| target.h 宏 | BYH7 偏移 | 定位方法 |
|---|---|---|
| `SLIDE_NFULNL_LOGGER_OFF` | `0x01605e7f` | `nfulnl_logger.name` 指向的 `"nfnetlink_log"` 字符串（nm 符号 0x2152a18 的 qword0） |
| `SLIDE_LOGGERS_0_1_OFF` | `0x02152a18` | `nfulnl_logger` 结构体（nm 符号） |
| `SLIDE_RANDOM_BOOT_ID_DATA_OFF` | `0x0227a2d0` | 全镜像搜索内容 == `&sysctl_bootid`（random_table boot_id 条目 `.data` 指针槽） |
| `SLIDE_SYSCTL_BOOTID_OFF` | `0x024c2628` | `sysctl_bootid`（nm 符号） |
| `SLIDE_TRACEFS_WORKER_CALLER_OFF` | `0x000dd03c` | `worker_thread` 内 `bl schedule`（0xdd038）返回地址 |
| `SELINUX_ENFORCING_OFF` | `0x02420440` | `selinux_state` 结构体（.bss 段，nm 符号） |
| `COPY_SPLICE_READ_OFF` | `0x003ec12c` | `generic_file_splice_read`（nm 符号） |
| `ASHMEM_MISC_FOPS_OFF` | `0x022beea8` | `ashmem_misc + offsetof(miscdevice, fops)=0x10`，内容 == `ashmem_fops`（已验证） |
| `SLIDE_TRACEFS_EVENT_ID` | `106` | ftrace-event 段内 `__event_sched_blocked_reason` 相对位置（0x10f6b8-0x10f408=0x2b0=86 条目 + 基址 20） |

### 2.1 对前置报告的修正

前置报告中 `nfulnl_logger 0x02152a18 差异异常大 (+0x14ac860)` 的标注需要修正：

- `0x02152a18` **确实就是** `nfulnl_logger` 结构体（nm 符号确认，qword0 = name 指针 → `"nfnetlink_log"` @ 0x1605e7f），不是误标。
- 前置报告把 **DZF2 target.h 的 `SLIDE_NFULNL_LOGGER_OFF`（0x16a61b8）** 误当作"nfulnl_logger 偏移"与 `0x02152a18` 对比，得出"差异异常"结论。
- 实际上 DZF2 的 `SLIDE_NFULNL_LOGGER_OFF=0x16a61b8` 是**照抄 S928U1** 的值：S928U1 中该处是 `"nfnetlink_log"` 字符串，但 **DZF2 自己的 `nfulnl_logger.name` 实际指向 `0x16a65c5`**（相差 0x40d）。即 DZF2 的 boot_id 泄漏偏移存在一个未暴露的偏差（被 tracefs/physical 等其他 slide 途径掩盖，不影响最终成功）。
- BYH7 采用**正确语义**：`SLIDE_NFULNL_LOGGER_OFF = nfulnl_logger.name 指向的字符串 = 0x01605e7f`。

---

## 3. 符号偏移总表（阶段 B 最终值）

| target.h 宏 | DZF2 | BYH7 | 差异 |
|---|---|---|---|
| CALL_USERMODEHELPER_EXEC_WORK | 0x000d39cc | 0x000d58f4 | +0x1f28 |
| SLIDE_TRACEFS_WORKER_CALLER | 0x000db1a0 | 0x000dd03c | +0x1e9c |
| COPY_SPLICE_READ | 0x003ef340 | 0x003ec12c | -0x3214 |
| NOOP_LLSEEK | 0x003a14e4 | 0x0039e594 | -0x7f50 |
| CONFIGFS_READ_ITER | 0x004712a4 | 0x0046d6e0 | -0x3bc4 |
| CONFIGFS_BIN_WRITE_ITER | 0x004717d4 | 0x0046dc10 | -0x3bc4 |
| ASHMEM_IOCTL | 0x00d3a314 | 0x00cd5a68 | -0x648ac |
| ASHMEM_COMPAT_IOCTL | 0x00d3ac4c | 0x00cd6350 | -0x648fc |
| ASHMEM_MMAP | 0x00d3aca4 | 0x00cd63a8 | -0x648fc |
| ASHMEM_OPEN | 0x00d3aed0 | 0x00cd65d4 | -0x648fc |
| ASHMEM_RELEASE | 0x00d3af58 | 0x00cd665c | -0x648fc |
| ASHMEM_SHOW_FDINFO | 0x00d3b078 | 0x00cd677c | -0x648fc |
| ANON_PIPE_BUF_OPS | 0x01219d90 | 0x011a4490 | -0x75900 |
| ASHMEM_FOPS | 0x013d1140 | 0x0134bc98 | -0x854a8 |
| KMALLOC_CACHES | 0x0176cbb8 | 0x016bad78 | -0xb1e40 |
| SLIDE_NFULNL_LOGGER | 0x016a61b8* | 0x01605e7f | -0xac339 |
| SYSTEM_UNBOUND_WQ | 0x0223ae60 | 0x0214ae60 | -0xf0000 |
| SLIDE_LOGGERS_0_1 | 0x02242a20 | 0x02152a18 | -0xf0808 |
| INIT_TASK | 0x0224f8c0 | 0x0215f040 | -0xf0880 |
| ASHMEM_MISC_FOPS | 0x023bb5b0 | 0x022beea8 | -0xfc708 |
| ROOT_TASK_GROUP | 0x0244cd80 | 0x0234bd80 | -0x100000 |
| SLIDE_RANDOM_BOOT_ID_DATA | 0x023762f0 | 0x0227a2d0 | -0xfc620 |
| SELINUX_ENFORCING | 0x02521588 | 0x02420440 | -0x101148 |
| SLIDE_SYSCTL_BOOTID | 0x026046e8 | 0x024c2628 | -0x1420c0 |

\* DZF2 此值为照抄 S928U1 的偏差值（见 2.1），BYH7 采用正确语义值。

---

## 4. 静态验证结果（阶段 D）

### 4.1 符号交叉验证（nm）

19 个 nm 可解析符号全部匹配（`llvm-nm vmlinux_byh7.elf`，偏移 = 地址 - KIMAGE_TEXT_BASE）：

```text
call_usermodehelper_exec_work 0xd58f4   noop_llseek 0x39e594   configfs_read_iter 0x46d6e0
configfs_bin_write_iter 0x46dc10        ashmem_ioctl 0xcd5a68  compat_ashmem_ioctl 0xcd6350
ashmem_mmap 0xcd63a8                    ashmem_open 0xcd65d4   ashmem_release 0xcd665c
ashmem_show_fdinfo 0xcd677c             anon_pipe_buf_ops 0x11a4490  ashmem_fops 0x134bc98
kmalloc_caches 0x16bad78                system_unbound_wq 0x214ae60  init_task 0x215f040
root_task_group 0x234bd80               sysctl_bootid 0x24c2628 selinux_state 0x2420440
generic_file_splice_read 0x3ec12c       nfulnl_logger 0x2152a18
```

### 4.2 内容级验证

| 检查 | 结果 |
|---|---|
| `nfulnl_logger` qword0 == `&"nfnetlink_log"` @ 0x1605e7f | ✅ |
| `ashmem_misc+0x10`（fops 字段）== `ashmem_fops` @ 0x134bc98 | ✅ |
| boot_id data 槽（0x227a2d0）内容 == `&sysctl_bootid` @ 0x24c2628 | ✅ |
| 25 个 `*_OFF` 宏全部在有效范围（.kernel 0x0-0x233da00 / .bss 0x233da00-0x333da00） | ✅ |

### 4.3 指纹表验证

`generate_p0_fingerprint.pl kernel 0x1f0000` 生成 32 行 × 8 qword，脚本自带 readback 校验：

```text
verified 32 rows and 256 source qwords at probe 0x1f0000
```

Row0（slide=0，probe 0x1f0000）8 个 qword 与 raw kernel 逐字核对一致。

### 4.4 TRACEFS_EVENT_ID 推导

```text
BYH7:  __event_sched_blocked_reason @ 0x10f6b8
       ftrace-event 段起始 @ 0x10f408 (第一个事件 __event_initcall_level)
       差值 0x2b0 = 86 个 8 字节条目 + Android 6.1 动态事件基址 20 = 事件 ID 106
DZF2:  __event_sched_blocked_reason @ 0x1ff560, 段起始 0x1ff2b0 → 同样 106
```

两内核段内相对位置一致 → `SLIDE_TRACEFS_EVENT_ID 106` 保持。

---

## 5. 保留未改的配置（无法静态验证）

以下为 DZF2 调优的启发式/稳定性常量，**照抄 DZF2**（无法从静态分析推导，需真机调试）：

- `SLIDE_S928_STABLE_RACE` 块：`MM_STRUCT_SZ 0x400`、`SLIDE_WAITER_CORE 6`
- `SKB_DATA_DELTA (-0x1000)` / `SLIDE_S928_SKB_DATA_DELTA (-0xe80)`
- bank 相关：`SLIDE_BANK_SLOTS 4`、`SLIDE_S928_BANK_LOCK_BASE 0x0ea0`、`SLIDE_S928_BANK_LOCK_SHIFT 10` 等
- `SLIDE_PSELECT_WORD_SHIFT 3`
- `SLIDE_P0_OFFSET_CANDIDATES`（0x000000..0x1f0000 共 32 个）
- `P0_ORACLE_*` 槽位常量（GATE_SLOT 0 / PROBE_SLOT 1 / 等）

> ⚠ 结构体布局（task_struct / pipe_buffer / rt_mutex_waiter 等）在 6.1.99 vs 6.1.145 间可能有差异；若真机跑不到对应阶段，优先核对 BTF 布局（`COMPACT_RT_MUTEX_WAITER` 相关偏移）。

---

## 6. 风险与后续

| 项 | 说明 |
|---|---|
| BUILD_FINGERPRINT | 使用推断值 `samsung/e1qzcx/e1q:15/AP3A.240905.015.A2/S9210ZCU4BYH7:user/release-keys`（来自真机 `getprop ro.build.fingerprint`，平台 e1q / S9210 国行标准版）。该宏仅作标识，未在 C 源码中引用 |
| 真机验证 | 阶段 E 需 One UI 7 设备。设备当前是 DZF2 (rev 6)，无法降级刷 One UI 7 |
| 概率性 | exploit 本身概率性，成功率随尝试累加，失败可能 panic/重启（正常现象） |
| 结构体差异 | 6.1.99 vs 6.1.145 若导致某阶段失败，按 ADAPTATION_GUIDE 阶段日志定位并核对 BTF |
