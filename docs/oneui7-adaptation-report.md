# One UI 7 (BYH7) S24 适配可行性报告

> 分析日期：2026-08-21
> 材料：`devices_ports/chc_oneui7_byh7_boot.img`（国行 S24, One UI 7 / Android 15）
> 结论先行：**可行，但需完整重新定标**。内核同为 6.1 GKI，漏洞适用；构建号差异大，全部符号偏移需重提取。
> **注：本报告中的内核符号提取、偏移对比与可行性分析，部分由 AI 辅助分析后生成，仅供技术研究与参考。**

---

## 1. 内核信息

| 项 | One UI 7 (BYH7) | 当前 One UI 8.5 (DZF2) |
|---|---|---|
| 内核版本 | **6.1.99**-android14-11-2370239 | 6.1.145-android14-11-3254743 |
| 构建号 | **2370239** | 3254743 |
| 机型标识 | abS9210ZCU4BYH7 | abS9280ZCS6DZF2 |
| 构建时间 | 2025-08-20 | 2026-06-11 |
| 平台 | e3q (S24 系列) | e3q (S24 系列) |
| 编译器 | clang 17.0.2 | clang 17.0.2 |
| KIMAGE_TEXT_BASE | 0xffffffc008000000 | 0xffffffc008000000 |

**结论**：同为 6.1 GKI + 同为 e3q 平台 + 同编译器 → **CVE-2026-43499 漏洞链大概率适用**。

---

## 2. 符号偏移对比（已提取 18 个）

| 符号 | DZF2 偏移 | BYH7 偏移 | 差异 |
|---|---|---|---|
| call_usermodehelper_exec_work | 0x000d39cc | 0x000d58f4 | +0x1f28 |
| noop_llseek | 0x003a14e4 | 0x0039e594 | -0x7f50 |
| configfs_read_iter | 0x004712a4 | 0x0046d6e0 | -0x3bc4 |
| configfs_bin_write_iter | 0x004717d4 | 0x0046dc10 | -0x3bc4 |
| ashmem_ioctl | 0x00d3a314 | 0x00cd5a68 | -0x648ac |
| compat_ashmem_ioctl | 0x00d3ac4c | 0x00cd6350 | -0x648fc |
| ashmem_mmap | 0x00d3aca4 | 0x00cd63a8 | -0x648fc |
| ashmem_open | 0x00d3aed0 | 0x00cd65d4 | -0x648fc |
| ashmem_release | 0x00d3af58 | 0x00cd665c | -0x648fc |
| ashmem_show_fdinfo | 0x00d3b078 | 0x00cd677c | -0x648fc |
| anon_pipe_buf_ops | 0x01219d90 | 0x011a4490 | -0x75900 |
| ashmem_fops | 0x013d1140 | 0x0134bc98 | -0x854a8 |
| kmalloc_caches | 0x0176cbb8 | 0x016bad78 | -0xb1e40 |
| system_unbound_wq | 0x0223ae60 | 0x0214ae60 | -0xf0000 |
| nfulnl_logger | 0x016a61b8 | 0x02152a18 | +0x14ac860 |
| init_task | 0x0224f8c0 | 0x0215f040 | -0xf0880 |
| root_task_group | 0x0244cd80 | 0x0234bd80 | -0x100000 |
| sysctl_bootid | 0x026046e8 | 0x024c2628 | -0x1420c0 |

> 注意：`nfulnl_logger` 差异异常大（+0x14ac860），需复核——可能是同名不同符号（.data 段 vs .rodata）。

**结论**：所有偏移均不同（差异 -0x1420c0 到 +0x14ac860），**不能复用 DZF2 target.h，必须逐符号重新定标**。

---

## 3. 待定位的特殊符号（需反汇编特征匹配）

以下符号在 vmlinux 符号表中不存在（非导出/被隐藏），当初 DZF2 也是通过内存特征/反汇编定位的：

| target.h 宏 | 说明 | 定位难度 |
|---|---|---|
| SELINUX_ENFORCING_OFF | `selinux_enforcing` 全局变量 | 中（搜 selinux 相关字符串引用） |
| COPY_SPLICE_READ_OFF | 匿名管道 read 回调 | 中（pipe 代码附近） |
| SLIDE_NFULNL_LOGGER_OFF | 需复核（已提取，差异异常） | 低 |
| SLIDE_LOGGERS_0_1_OFF | nf_loggers 数组 | 中 |
| SLIDE_RANDOM_BOOT_ID_DATA_OFF | random boot id 数据 | 中 |
| SLIDE_TRACEFS_WORKER_CALLER_OFF | tracefs worker 调用点 | 高（需精确定位） |
| P0_ORACLE 系列（probe target image 等） | 物理内存 oracle | 高 |

---

## 4. 可行性评估

### 乐观因素 ✓
1. **漏洞适用**：6.1 GKI + e3q 平台 + 同编译器，CVE-2026-43499 链路完整
2. **18 个核心符号已提取**：占 target.h 偏移的 70%+
3. **方法成熟**：DZF2 已完整走通过一次，流程完全复用
4. **指纹表可生成**：p0_fingerprint 从内核 Image 提取，有现成脚本

### 风险因素 ⚠
1. **特殊符号定位工作量大**：selinux_enforcing、copy_splice_read 等 5-7 个需要反汇编特征匹配，可能需数小时到数天
2. **KASLR 指纹需重生成**：32 行 × 8 qword 指纹表必须从 BYH7 内核重提
3. **结构体布局可能变化**：6.1.99 vs 6.1.145，task_struct/pipe_buffer 等偏移可能有变
4. **无真机验证**：手上设备是 DZF2 (rev 6)，**不能降级刷 One UI 7**，需 One UI 7 设备实测
5. **风险不可忽视**：即使全部定标完成，真机跑通仍有不确定性（概率性 exploit）

---

## 5. 建议路线

```
阶段 A（已完成）：提取内核 → 恢复 vmlinux → 提取 18 个符号偏移
阶段 B（进行中）：反汇编定位 5-7 个特殊符号 → 生成 BYH7 target.h
阶段 C：重生成 p0_fingerprint 指纹表 → 编译 payload
阶段 D：静态验证（偏移合理性检查）
阶段 E（需真机）：One UI 7 设备实测 → 逐阶段日志验证
```

**当前卡点**：阶段 B 的特殊符号定位 + 阶段 E 的真机（One UI 7 设备需另备一台，或等有 One UI 7 固件的用户提供材料）。

---

## 6. 材料清单

```
/home/nt/root_research/devices_ports/
├── chc_oneui7_byh7_boot.img        # One UI 7 boot 分区 (100MB)
└── unpacked/
    ├── kernel                      # 提取的内核 Image (36.9MB)
    └── vmlinux_byh7.elf            # 恢复符号的 ELF (41.8MB) ← 定标核心资产
```

**下一步需要**：
1. （可选）One UI 7 真机或更多 One UI 7 固件材料
2. 决定是否投入阶段 B 的特殊符号定位工作

---

> 返回 [文档中心](README.md) · [根 README](../README.md)
