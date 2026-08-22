# 港版 DZE2 适配定标完成报告

> 定标日期：2026-08-22
> 目标：`src/targets/e3q-S9280ZHS6DZE2/`（港版 S9280, One UI 8.5, kernel 6.1.145）
> 素材：`devices_ports/tgy_oneui8.5_dze2_boot.img`（TGY 港版 CSC）

---

## 1. 内核信息

| 项 | 港版 DZE2 | 国行 DZF2（参考） | 美版 S928U1 DZF2 |
|---|---|---|---|
| 构建号 | **33419968** | 3254743 | **33419968** |
| 机型标识 | abS9280ZHS6DZE2 | abS9280ZCS6DZF2 | abS928USQS6DZF2 |
| 内核版本 | 6.1.145-android14-11 | 同 | 同 |
| 编译器 | clang 17.0.2 | 同 | 同 |
| KIMAGE_TEXT_BASE | 0xffffffc008000000 | 同 | 同 |

**关键结论**：港版 DZE2 构建号 **33419968 与美版 S928U1 相同**（而非国行 DZF2 的 3254743），因此符号布局更接近 S928U1。

---

## 2. 定标结果

### 2.1 符号偏移对比（20 个 nm 可解析符号全部提取）

| 符号 | DZE2 偏移 | DZF2 国行 | 一致 |
|---|---|---|---|
| call_usermodehelper_exec_work | 0x000d39cc | 0x000d39cc | = |
| noop_llseek | 0x003a14e4 | 0x003a14e4 | = |
| configfs_read_iter | 0x004712a4 | 0x004712a4 | = |
| configfs_bin_write_iter | 0x004717d4 | 0x004717d4 | = |
| generic_file_splice_read | 0x003ef340 | 0x003ef340 | = |
| ashmem_ioctl | 0x00d3a314 | 0x00d3a314 | = |
| compat_ashmem_ioctl | 0x00d3ac4c | 0x00d3ac4c | = |
| ashmem_mmap | 0x00d3aca4 | 0x00d3aca4 | = |
| ashmem_open | 0x00d3aed0 | 0x00d3aed0 | = |
| ashmem_release | 0x00d3af58 | 0x00d3af58 | = |
| ashmem_show_fdinfo | 0x00d3b078 | 0x00d3b078 | = |
| anon_pipe_buf_ops | 0x01219d90 | 0x01219d90 | = |
| ashmem_fops | 0x013d1140 | 0x013d1140 | = |
| **kmalloc_caches** | **0x0176c6f8** | **0x0176cbb8** | **!=（=S928U1）** |
| system_unbound_wq | 0x0223ae60 | 0x0223ae60 | = |
| nfulnl_logger | 0x02242a20 | 0x02242a20 | = |
| init_task | 0x0224f8c0 | 0x0224f8c0 | = |
| root_task_group | 0x0244cd80 | 0x0244cd80 | = |
| selinux_state | 0x02521588 | 0x02521588 | = |
| sysctl_bootid | 0x026046e8 | 0x026046e8 | = |

**结论：19/20 与国行 DZF2 一致，唯一差异 `kmalloc_caches`（0x176c6f8 = 美版 S928U1 值）。**

### 2.2 内容级符号验证

| 符号 | 值 | 验证方法 | 结果 |
|---|---|---|---|
| `SLIDE_NFULNL_LOGGER` | 0x016a61e6 | `nfulnl_logger.name` 指向的 `"nfnetlink_log"` 字符串 | OK（与 DZF2 的 0x16a61b8 相差 0x2e，按 DZE2 实测语义值） |
| `SLIDE_RANDOM_BOOT_ID_DATA` | 0x023762f0 | 内容 == `&sysctl_bootid` | OK |
| `SLIDE_TRACEFS_EVENT_ID` | 106 | `__event_sched_blocked_reason` 段内相对位置 | OK（与 DZF2 相同） |
| `ASHMEM_MISC_FOPS` | 0x023bb5b0 | `ashmem_misc + 0x10` == `ashmem_fops` | OK（沿用 DZF2） |

### 2.3 指纹表

`generate_p0_fingerprint.pl kernel 0x1f0000` 生成 32 行 × 8 qword，脚本 readback 校验：

```text
verified 32 rows and 256 source qwords at probe 0x1f0000
```

Row0（slide=0，probe 0x1f0000）8 个 qword 与 raw kernel 逐字核对一致。

---

## 3. 产物

```text
src/targets/e3q-S9280ZHS6DZE2/
├── target.h            # 25 个 *_OFF 宏 + 配置宏
└── p0_fingerprint.h    # 32 行 × 8 qword 指纹表
build/e3q-S9280ZHS6DZE2/
├── cve-2026-43499-app.stable.so  # 104128 B
└── cve-2026-43499-root           # 27072 B
```

构建命令：

```bash
ANDROID_NDK_HOME=/home/nt/root_research/06_工具链/android-ndk-r29 \
  TARGET=e3q-S9280ZHS6DZE2 make stable
```

---

## 4. 与 App 集成

- 载荷已复制为 `app/src/main/assets/cve-2026-43499-dze2`
- `FirmwareVersion` 枚举新增 `DZE2("cve-2026-43499-dze2", "One UI 8.5", "港版 DZE2")`
- 修正：原 DZF2 条目 range "DZE2 – DZF2" 有误导性——**港版 DZE2 的 kmalloc_caches 与国行 DZF2 不同，不能用 DZF2 载荷**，现拆分为独立条目

---

## 5. 风险与后续

| 项 | 说明 |
|---|---|
| 真机验证 | 阶段 E 需港版 DZE2 真机。载荷为静态验证通过，未真机运行 |
| 结构体差异 | 构建号与 S928U1 相同，task_struct/pipe_buffer 布局应与 S928U1 一致，风险低 |
| 概率性 | exploit 本身概率性，失败/重启属正常现象 |
| `SLIDE_NFULNL_LOGGER` | DZF2 照抄 S928U1 值（0x16a61b8）仍有偏差仍成功；DZE2 用实测语义值 0x16a61e6，更精确 |
