# 港版 CZA1 适配定标完成报告（One UI 8.0）

> 定标日期：2026-08-22
> 目标：`src/targets/e3q-S9280ZHS4CZA1/`（港版 S9280, One UI 8.0, kernel 6.1.128）
> 素材：`devices_ports/tgy_oneui8.0_cza1_boot.img`（TGY 港版 CSC）

---

## 1. 内核信息

| 项 | 港版 CZA1 | 港版 DZE2 | 国行 DZF2 |
|---|---|---|---|
| 构建号 | **31999054** | 33419968 | 3254743 |
| 机型标识 | abS9280ZHS4CZA1 | abS9280ZHS6DZE2 | abS9280ZCS6DZF2 |
| 内核版本 | **6.1.128**-android14-11 | 6.1.145-android14-11 | 6.1.145-android14-11 |
| One UI | **8.0** | 8.5 | 8.5 |
| Android | 16 | 14（内核串） | 14（内核串） |
| 编译器 | clang 17.0.2 | 同 | 同 |
| KIMAGE_TEXT_BASE | 0xffffffc008000000 | 同 | 同 |

**关键差异：CZA1 内核版本 6.1.128，与 DZF2/DZE2 的 6.1.145 不同，全部函数符号偏移均不同，需全新定标。**

---

## 2. 符号定标结果

### 2.1 函数/数据符号（20 个，nm 提取 + 交叉验证全部通过）

| 符号 | CZA1 (6.1.128) | DZF2 (6.1.145) | 差异 |
|---|---|---|---|
| call_usermodehelper_exec_work | 0x000d308c | 0x000d39cc | Δ0x940 |
| worker_thread | 0x000da7a8 | 0x000db100 | Δ0x958 |
| noop_llseek | 0x0039e518 | 0x003a14e4 | Δ0x2fcc |
| generic_file_splice_read | 0x003ec2c8 | 0x003ef340 | Δ0x3078 |
| configfs_read_iter | 0x0046dcc0 | 0x004712a4 | Δ0x35e4 |
| configfs_bin_write_iter | 0x0046e1f0 | 0x004717d4 | Δ0x35e4 |
| ashmem_ioctl | 0x00cd98c4 | 0x00d3a314 | Δ0x60a50 |
| compat_ashmem_ioctl | 0x00cda1fc | 0x00d3ac4c | Δ0x60a50 |
| ashmem_mmap | 0x00cda254 | 0x00d3aca4 | Δ0x60a50 |
| ashmem_open | 0x00cda480 | 0x00d3aed0 | Δ0x60a50 |
| ashmem_release | 0x00cda508 | 0x00d3af58 | Δ0x60a50 |
| ashmem_show_fdinfo | 0x00cda628 | 0x00d3b078 | Δ0x60a50 |
| anon_pipe_buf_ops | 0x011a6bd0 | 0x01219d90 | Δ0x731c0 |
| ashmem_fops | 0x0134e7b0 | 0x013d1140 | Δ0x82990 |
| kmalloc_caches | 0x016c08f8 | 0x0176cbb8 | Δ0xac2c0 |
| system_unbound_wq | 0x0214ae60 | 0x0223ae60 | Δ0xf0000 |
| nfulnl_logger | 0x02152a20 | 0x02242a20 | Δ0xf0000 |
| init_task | 0x0215f8c0 | 0x0224f8c0 | Δ0xf0000 |
| root_task_group | 0x0234cd80 | 0x0244cd80 | Δ0x100000 |
| selinux_state | 0x02421460 | 0x02521588 | Δ0x100128 |
| sysctl_bootid | 0x024c35a8 | 0x026046e8 | Δ0x141140 |

### 2.2 内容级符号验证（ELF 内存读取交叉验证）

| 符号 | CZA1 值 | 验证方法 | 结果 |
|---|---|---|---|
| `ASHMEM_MISC_FOPS` | 0x022bfbf0 | `ashmem_miscs.fops` 指针槽，内容 == `ashmem_fops` | OK |
| `SLIDE_S928_PROBE_TARGET_IMAGE` | 0x022bfbf0 | = ASHMEM_MISC_FOPS（DZF2 同构） | OK |
| `SLIDE_NFULNL_LOGGER` | 0x0160b705 | `nfulnl_logger.name` 指向 `"nfnetlink_log"` | OK |
| `SLIDE_RANDOM_BOOT_ID_DATA` | 0x0227ab38 | 内容 == `&sysctl_bootid` | OK |
| `SLIDE_TRACEFS_WORKER_CALLER` | 0x000da848 | `worker_thread + 0xa0`（反汇编验证与 DZF2 同构） | OK |
| `SLIDE_TRACEFS_EVENT_ID` | 106 | 与 DZF2/DZE2/BYH7 相同（S 系列统一值） | 沿用 |

### 2.3 结构体布局（BTF 验证，与 DZF2 全部一致）

| 结构体 | CZA1 BTF | DZF2 target.h | 一致 |
|---|---|---|---|
| struct page size | 0x40 | 0x40 | = |
| struct slab.slab_cache | **0x18** | 0x18 | = |
| struct page.compound_head | 0x08 | 0x08 | = |
| struct page.type | 0x30 | 0x30 | = |
| task_struct pi_lock/pi_waiters/pi_top_task/pi_blocked_on | 0x924/0x938/0x948/0x950 | 同 | = |
| rt_mutex_waiter (size 0x58) | tree@0x00 pi_tree@0x18 task@0x30 lock@0x38 | 同 | = |
| pipe_buffer (size 0x28) | page@0x00 offset@0x08 len@0x0c ops@0x10 flags@0x18 | 同 | = |
| file_operations | owner@0x00 … ioctl@0x50 … splice_read@0xc8 | 同 | = |
| mm_struct | **0x3c0** | 0x400（DZF2 注释值） | **修正为 0x3c0** |
| workqueue_struct dfl_pwq | 0xb0 | 0xb0 | = |
| worker_pool worklist/nr_idle | 0x28/0x3c | 同 | = |

**重要修正：`MM_STRUCT_SZ` 从 DZF2 的 0x400 改为实测 0x3c0**（BTF 实测，DZG1 6.1.145 也是 0x3c0，DZF2 的 0x400 可能是旧值，用实测值更稳）。

---

## 3. 指纹表

`generate_p0_fingerprint.pl kernel 0x1f0000` 生成 32 行 × 8 qword，readback 校验通过：

```text
verified 32 rows and 256 source qwords at probe 0x1f0000
```

Row0（slide=0）与 raw kernel 逐字核对一致。

---

## 4. 产物

```text
src/targets/e3q-S9280ZHS4CZA1/
├── target.h
└── p0_fingerprint.h
build/e3q-S9280ZHS4CZA1/
├── cve-2026-43499-app.stable.so  # 104128 B
└── cve-2026-43499-root           # 27072 B
```

构建：

```bash
ANDROID_NDK_HOME=/home/nt/root_research/06_工具链/android-ndk-r29 \
  TARGET=e3q-S9280ZHS4CZA1 make stable
```

---

## 5. 验证矩阵

| 检查项 | 结果 |
|---|---|
| 20/20 nm 符号与 target.h 交叉验证 | ✅ 全部匹配 |
| 指纹表 readback + Row0 逐字核对 | ✅ |
| ashmem_misc.fops 指针槽内容验证 | ✅ |
| nfulnl_logger.name → "nfnetlink_log" | ✅ |
| boot_id 指针槽 → &sysctl_bootid | ✅ |
| worker_thread 反汇编结构一致性 | ✅ |
| 全部结构体 BTF 布局对比 | ✅ 一致（MM_STRUCT_SZ 修正为 0x3c0） |

---

## 6. 风险与后续

| 项 | 说明 |
|---|---|
| 真机验证 | 需港版 One UI 8.0 真机（S9280ZHS4CZA1） |
| `SLIDE_TRACEFS_EVENT_ID=106` | S 系列统一值，但 6.1.128 的 tracefs 事件注册顺序若有变会失败；若 slide-kaslr source=tracefs 失败需重查 |
| 结构体差异 | BTF 实测与 DZF2 一致，风险低 |
| One UI 8.0 差异 | App 域 cgroup/缓存归属可能有差异（参考 SM-S9380 经验），真机需走 App 完整验证 |
| 概率性 | exploit 本身概率性，失败/重启属正常现象 |

---

> 返回 [文档中心](README.md) · [根 README](../README.md)
