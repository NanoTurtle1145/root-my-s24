# DZG1 适配定标完成报告

> 定标日期：2026-08-21
> 目标：`src/targets/e3q-S9280ZCS6DZG1/`（国行 S9280, One UI 8.5, kernel 6.1.145-android14-11-3254743-abS9280ZCS6DZG1）
> 来源：`devices_ports/chc_oneui8.5_dzg1_boot.img`（原误命名 tgy_，实为国行 CSC）

## 1. 背景

- 测试者真机为**港版** `S9280ZHS6DZG1`（构建 33419968），发送给我们的 boot 实为**国行** `S9280ZCS6DZG1`（构建 3254743）
- 国行 DZG1 构建号 **3254743 与 DZF2 相同**，函数符号完全一致
- 但数据区内容有微小偏移（3.43% 字节差异），需单独验证内容型符号

## 2. 验证结论：与 DZF2 对比

| 类别 | 符号/宏 | DZF2 | DZG1 | 一致 |
|---|---|---|---|---|
| 函数 | call_usermodehelper_exec_work | 0x0d39cc | 0x0d39cc | ✅ |
| 函数 | ashmem_ioctl/mmap/open/release/show_fdinfo | 0xd3a314 等 | 同 | ✅ |
| 函数 | configfs_read_iter / bin_write_iter | 0x4712a4 / 0x4717d4 | 同 | ✅ |
| 函数 | generic_file_splice_read / noop_llseek | 0x3ef340 / 0x3a14e4 | 同 | ✅ |
| 数据 | anon_pipe_buf_ops | 0x1219d90 | 0x1219d90 | ✅ |
| 数据 | ashmem_fops | 0x13d1140 | 0x13d1140 | ✅ |
| 数据 | kmalloc_caches | 0x176cbb8 | 0x176cbb8 | ✅ |
| 数据 | nfulnl_logger（对象） | 0x2242a20 | 0x2242a20 | ✅ |
| 数据 | random_table / boot_id slot | 0x23761e8 / +0x108 | 同 | ✅ |
| 数据 | sysctl_bootid | 0x26046e8 | 0x26046e8 | ✅ |
| 数据 | init_task / root_task_group | 0x224f8c0 / 0x244cd80 | 同 | ✅ |
| 数据 | selinux_state（enforcing@0） | 0x2521588 | 0x2521588 | ✅ |
| 配置 | TRACEFS_EVENT_ID | 106 | 106 | ✅ |
| 配置 | worker_thread caller | 0xdb1a0 | 0xdb1a0 | ✅ |
| **内容** | **nfulnl_logger.name 字符串** | **0x16a65c5** | **0x16a6547** | ❌ **-0x7e** |

## 3. 关键修正

**DZF2 target.h 的 `SLIDE_NFULNL_LOGGER_OFF=0x16a61b8` 是 S928U1 复制的错位值**
（DZF2 实际 name 在 0x16a65c5，此前靠 tracefs 兜底掩盖了 boot_id 路径失败）

DZG1 的 nfulnl_logger.name 实际在 **`0x16a6547`**，已作为 DZG1 专用值写入 target.h。

## 4. 产出物

```text
src/targets/e3q-S9280ZCS6DZG1/
├── target.h            # DZF2 基线 + DZG1 修正（label/指纹/logger 偏移）
└── p0_fingerprint.h    # 32 行 × 8 qword 指纹（DZG1 内核 @ 0x1f0000，脚本自验证通过）
build/e3q-S9280ZCS6DZG1/cve-2026-43499-app.stable.so  # 104128 B
```

指纹生成：
```bash
perl generate_p0_fingerprint.pl devices_ports/dzg1_unpacked/kernel 0x1f0000 \
  src/targets/e3q-S9280ZCS6DZG1/p0_fingerprint.h   # verified 32 rows and 256 source qwords
```

## 5. 真机验证（待测）

- 目标：国行 S9280ZCS6DZG1 设备
- 预期：boot_id 泄漏路径修正后成功率高于 DZF2 载荷（此前 DZG1 上 DZF2 载荷概率性失败）
- 注：港版 S9280ZHS6DZG1（构建 33419968）符号未知，需单独提取内核定标

---

> 返回 [文档中心](README.md) · [根 README](../README.md)
