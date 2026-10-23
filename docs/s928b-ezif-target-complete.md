# SM-S928B 欧版 S24 Ultra（e3q）EZIF 适配定标报告

> 定标日期：2026-10-18
> 状态：**定标完成，2026-10-23 真机实测通过**（v3.5.2 alpha 起 `tested=true`，无需再开「未经测试的载荷」）
> 目标：`fusion-s24u` → `src/targets/e3q-S928BXXU6EZIF/`
> 素材：`devices_ports/ezif_unpacked/`（SM-S928B 欧版 S24 Ultra，S928BXXU6EZIF，One UI 9.0；boot.img sha256 `b98326455c825062e5350ae88b75488f4de4c7b1e587a47105fffdc41be37500`，Samsung 签名 `SM-S928B_EUR_XX_QKEY2`）
> 触发：欧版 S24 Ultra 更新 One UI 9.0（内核 6.1.145 → 6.1.162 代际跃升），S24 全系现有载荷全部不适用

---

## 1. 内核信息

| 项 | SM-S928B EZIF（欧版 / One UI 9.0） | 港版 S9280 DZE2（主模板） |
|---|---|---|
| 内核版本 | `6.1.162-android14-11-34343818-abS928BXXU6EZIF` | `6.1.145-android14-11-33419968-abS9280ZHS6DZE2` |
| 构建号 | **34343818** | 33419968 |
| 构建时间 | 2026-09-19 08:12 UTC | 2026-05-19 |
| 编译器 | clang 17.0.2 (r487747c) | 同 |
| KIMAGE_TEXT_BASE | `0xffffffc008000000` | 同 |
| raw kernel | 38,140,416 B（image_size 0x2710000） | 38,005,248 B |
| 恢复出的 ELF | `vmlinux_ezif.elf`（43,222,470 B，107,567 条符号记录） | 43,070,883 B / 107,253 条 |

**内核代际**：6.1.145 → 6.1.162（+17 个 LTS 补丁版本）；KMI 仍为 `android14-6.1`，但段内布局全量位移，**偏移不能沿用任何旧载荷**。

## 2. 定标方法

1. 从 `S24Ultra Europe One UI 9.0.zip` 解出 boot.img → raw ARM64 Image（38,140,416 B）
2. `vmlinux-to-elf` → `vmlinux_ezif.elf`（107,567 条符号记录）
3. 以港版 DZE2 为模板做**符号身份迁移**：模板 `*_OFF` → 模板 ELF 符号名 → EZIF ELF 同符号新地址（23 项）
4. 2 项非符号地址（字符串 / 数据结构槽）按 EZIF 镜像逐字节实测（见 §4）
5. P0 指纹用本内核原始 Image 全量重生成（32 行 × 8 qword，readback 校验通过）

## 3. 符号偏移全量迁移（25 项变化宏）

以港版 DZE2 为模板重标 23 项；另 2 项按镜像实测（§4）。其余 60 项结构体字段/常量偏移保持模板值（6.1 KMI 稳定分支冻结）。

| 宏 | 港版 DZE2 | EZIF（新） | Δ |
|---|---|---|---|
| `ANON_PIPE_BUF_OPS_OFF` | 0x001219d90 | 0x00122ad50 | +0x10fc0 |
| `ASHMEM_COMPAT_IOCTL_OFF` | 0x000d3ac4c | 0x000d457c4 | +0xab78 |
| `ASHMEM_FOPS_OFF` | 0x0013d1140 | 0x0013e2e30 | +0x11cf0 |
| `ASHMEM_IOCTL_OFF` | 0x000d3a314 | 0x000d44e8c | +0xab78 |
| `ASHMEM_MISC_FOPS_OFF` | 0x0023bb5b0 | 0x0023dc200 | +0x20c50 |
| `ASHMEM_MMAP_OFF` | 0x000d3aca4 | 0x000d4581c | +0xab78 |
| `ASHMEM_OPEN_OFF` | 0x000d3aed0 | 0x000d45a48 | +0xab78 |
| `ASHMEM_RELEASE_OFF` | 0x000d3af58 | 0x000d45ad0 | +0xab78 |
| `ASHMEM_SHOW_FDINFO_OFF` | 0x000d3b078 | 0x000d45bf0 | +0xab78 |
| `CALL_USERMODEHELPER_EXEC_WORK_OFF` | 0x0000d39cc | 0x0000d3be0 | +0x214 |
| `CONFIGFS_BIN_WRITE_ITER_OFF` | 0x0004717d4 | 0x0004730b4 | +0x18e0 |
| `CONFIGFS_READ_ITER_OFF` | 0x0004712a4 | 0x000472b84 | +0x18e0 |
| `COPY_SPLICE_READ_OFF` | 0x0003ef340 | 0x0003f07c4 | +0x1484 |
| `INIT_TASK_OFF` | 0x00224f8c0 | 0x00226f6c0 | +0x1fe00 |
| `KMALLOC_CACHES_OFF` | 0x00176c6f8 | 0x001781458 | +0x14d60 |
| `NOOP_LLSEEK_OFF` | 0x0003a14e4 | 0x0003a235c | +0xe78 |
| `ROOT_TASK_GROUP_OFF` | 0x00244cd80 | 0x00246dd80 | +0x21000 |
| `SELINUX_ENFORCING_OFF` | 0x002521588 | 0x002542618 | +0x21090 |
| `SLIDE_LOGGERS_0_1_OFF` | 0x002242a20 | 0x002262828 | +0x1fe08 |
| `SLIDE_NFULNL_LOGGER_OFF` | 0x0016a61e6 | 0x0016ba45d | +0x14277 |
| `SLIDE_RANDOM_BOOT_ID_DATA_OFF` | 0x0023762f0 | 0x002396a98 | +0x207a8 |
| `SLIDE_S928_PROBE_TARGET_IMAGE_OFF` | 0x0023bb5b0 | 0x0023dc200 | +0x20c50 |
| `SLIDE_SYSCTL_BOOTID_OFF` | 0x0026046e8 | 0x002625768 | +0x21080 |
| `SLIDE_TRACEFS_WORKER_CALLER_OFF` | 0x0000db1a0 | 0x0000db39c | +0x1fc |
| `SYSTEM_UNBOUND_WQ_OFF` | 0x00223ae60 | 0x00225ac60 | +0x1fe00 |

> 位移模式：text 段 +0x1fc ~ +0xab78；data/rodata/bss 段 +0x14277 ~ +0x21090。逐符号位移不等，印证「按符号名迁移」是唯一可靠方式。

## 4. 两项手工实测（非符号地址）

### 4.1 `SLIDE_NFULNL_LOGGER_OFF = 0x016ba45d`

- 镜像中 `nfnetlink_log\0` 字符串**唯一出现**于 `0x16ba45d`（港版 `0x16a61e6`）；
- 双重验证：`nfulnl_logger` 符号（`0x02262828`）首 qword = `0xffffffc0096ba45d`，减 `KIMAGE_TEXT_BASE` 恰好 = `0x016ba45d`（即结构体 .name 指针指向该字符串）；
- 用途：`slide_read_stext()` 由 boot_id 泄漏指针反推 `stext = leaked - 0x016ba45d`。

### 4.2 `SLIDE_RANDOM_BOOT_ID_DATA_OFF = 0x02396a98`

- 语义：`random_table[]`（sysctl 随机数表）中 **boot_id 表项**（entry4，+0x100）的 `.data` 指针槽（+0x108）——fops.c / util.c / slide_app.c 共用的锚定可写数据槽；
- 验证：EZIF `random_table` = `0x02396990`，+0x108 处 qword = `0xffffffc00a625768` = `sysctl_bootid` 符号地址；港版同构（`0x023761e8` +0x108 = 港版 `sysctl_bootid`），双内核互证 `+0x108` 结构规则不变。

## 5. CVE 链核查（remove_waiter）

- EZIF `remove_waiter`：**154 条指令**（港版同为 154 条）；
- 关键访问按相同相对偏移出现：`str xzr,[x20,#0x950]`（+0x8c，清 `current->pi_blocked_on`）、`ldr x8,[x21,#0x950]`（+0x1a4，经 owner 读阻塞 waiter），与港版逐一对齐；
- 归一化（助记符/寄存器/操作数形状）指令序列对比：**0 差异 / 154 条**；原始字节差异 48 条，全部为分支/调用目标的重定位位移；
- 结论：**「清 current 而非 waiter->task」的竞态窗口在 6.1.162 上原样保留**，exploit 链假设成立。

## 6. tracefs 预判参数复核

- `SLIDE_TRACEFS_EVENT_ID = 106` **维持**：`sched_blocked_reason` 在 `__ftrace_events` 链接表中的索引港版/EZIF 同为 **86**，加注册保留号段 20 → 106；
- 真机复核命令：`cat /sys/kernel/tracing/events/sched/sched_blocked_reason/id`；
- `SLIDE_TRACEFS_WORKER_CALLER_OFF = worker_thread + 0xA0` 规则：EZIF `worker_thread` = `0xdb2fc` → `0xdb39c` ✓。

## 7. P0 指纹（KASLR）

- 生成：`generate_p0_fingerprint.pl <raw Image> 0x1f0000 p0_fingerprint.h`，32 行 × 8 qword 自校验通过；
- 与旧版差异：vs 港版 **235/256** qword 不同；vs 美版同为 **235/256**（内核代际变化导致指令数据全面更替，必须全新指纹）；
- 产物 sha256 `06d0fb6ea4aa285cbcd0e04cceb7e6f1b0f1ae1adafdf30b82ff6dbdec32360c`。

## 8. 构建

```bash
ANDROID_NDK_HOME=/home/nt/项目/samsung_root_research/06_工具链/android-ndk-r29 \
  make TARGET=e3q-S928BXXU6EZIF stable all
```

| 产物 | 大小 | sha256 |
|---|---|---|
| `cve-2026-43499-app.stable.so`（App 资产） | 104,128 B（尺寸约束） | `5215587861d4a6ae2ddb9d7437dee3d71bdb0910b0542f789e4478a866ac6be6` |
| `cve-2026-43499-app.so` | 127,392 B | `9e247f52f4588c093eeff88d0d922e004ce00f90c77e0772e3b3c83d00234a95` |
| `cve-2026-43499-root` | 27,496 B | `d2dccd05d027fde632301613e65337274ef72f395173d6ffb48ceef50babba19` |

内嵌标签 `e3q-S928BXXU6EZIF-app-physical-p0-oracle` 校验通过；正式 target 与草案构建逐字节一致（`target.h` sha256 `320b113d…`）。

### KernelSU 3.3.0（32601）

| 产物 | 大小 | sha256 |
|---|---|---|
| `kernelsu-EZIF.ko`（内核驱动） | 404,840 B | `7973e28bff2a04aa2c9d4e9692307dd7a7999cf3833dd9e3d3fef6fe3066795a` |
| `ksud-ezif-32601`（内嵌驱动的 ksud 资产） | 5,107,192 B | `51881cdd113adb80cf00e18669ebb8a8fa4af00c27ae4a9a88ad7fdc704c00fe` |

- 模块 vermagic 与真机逐字节一致：`6.1.162-android14-11-34343818-abS928BXXU6EZIF SMP preempt mod_unload modversions aarch64`
- 静态审计通过：未定义符号 202 / 缺失 0 / `kallsyms` 解析 70 / CRC 不一致 0；与 F741N 专属模块节级对比仅 `.modinfo`（vermagic）与 build-id 两节不同，与已发货 DZF2/DZH3 模块对比除 `.modinfo`、build-id 及 `.symtab` 少量字节外逐字节一致
- 构建管线：`adapt-ksu.sh EZIF vmlinux_ezif.elf android14-6.1`（DDK 容器 + 目标内核 Module.symvers/vermagic 注入）
- 配套管理器需安装**官方 KernelSU v3.3.0 (32601)**；本机型未构建 3.2.5 旧版驱动，异常时界面无可回退选项

## 9. App 集成

- 载荷资产：`cve-2026-43499-S928BXXU6EZIF`（条目 `S928BEZIF`，分组「港版/台版（外版）」）
- KSU 驱动资产：`ksud-ezif-32601`（32601 / 3.3.0；vermagic 唯一对应本内核，不可与其它机型混用）
- root 守护进程沿用共享资产 `cve-2026-43499-root`（与机型无关，UMH 协议未变）
- `tested=true`（2026-10-23 真机实测通过，本版起按已实测发布）；服务端 `payloads.json` 条目需同步翻转 `tested`

## 10. 验证清单

- [x] 符号定标（nm 全量迁移 + 逐字节复核）
- [x] p0 指纹生成（readback 256/256）
- [x] 正式 target 构建（104,128 B；与草案逐字节一致）
- [x] KernelSU 驱动构建 + 静态审计
- [ ] 服务端清单条目翻转 `tested=true`（随本版发布同步）
- [x] **真机验证**（2026-10-23 通过；采集 `getprop ro.build.fingerprint` 与 `cat /sys/kernel/tracing/events/sched/sched_blocked_reason/id`，按 `ADAPTATION_GUIDE_FULL.md` §8 流程）

## 11. 未采纳项与备注

- `BUILD_FINGERPRINT` 暂为占位串：仅元数据、不参与产物逻辑，拿到真机 `getprop ro.build.fingerprint` 后回填
- `SLIDE_TRACEFS_EVENT_ID = 106` 为推断值（港版实测 106 同构），真机以 `.../sched_blocked_reason/id` 复核为准
- 欧版归入既有「港版/台版（外版）」分组（沿 SM-F966B 欧版 Z Fold7 先例）；若后续欧版机型增多可单开分组
