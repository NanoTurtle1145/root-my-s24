# SM-S721B（Galaxy S24 FE）98260 载荷定标

> 状态：**静态定标完成，待真机验证**（issue #49）。载荷已随 alpha 渠道发布。

## 目标

| 项 | 值 |
|---|---|
| 机型 | SM-S721B（Galaxy S24 FE，`r12s`，Exynos 2400e，OXM） |
| 固件 | `S721BXXUEEZIF`（One UI 9.0 / Android 17，安全补丁 2026-09-05） |
| 内核 | `6.1.162-android14-11`（**无** `-<build>-ab<suffix>` 后缀），2026-09-19 构建 |
| 载荷 | 引擎 98260（posix-cpu-timers UAF）；CVE 在 6.1.191 才修，本内核仍存在 |

## 关键结论

1. **不能套用 S24 载荷**：S24 的 kernel release 是
   `6.1.162-android14-11-34343818-abS9280ZHS6EZJ1`，FE 是 `6.1.162-android14-11`，
   是另一份内核（此前 S721B 用 GhostLock 载荷的尝试都是 `failed@stage3`）。
2. **结构体布局与 EZIF 完全一致**：把两份内核的 BTF 逐字段 diff，
   `k_itimer / cred / task_struct / pipe_inode_info / pipe_buffer / work_struct /
   pool_workqueue / workqueue_struct / worker_pool / posix_cputimers / signal_struct /
   page / user_struct / ucounts / user_namespace` 的大小与成员偏移**全部相同**。
   因此只需要替换 per-build 的**符号地址**与 3 个 `kmem_cache_alloc` 调用点。

## 定标方法（已在 EZIF 上回放验证）

1. `unpack_bootimg` 取 `kernel`（ARM64 Image）；
2. `vmlinux-to-elf` 从 kallsyms 还原符号（r12s：115,698 个符号，base `0xffffffc008000000`）；
3. 内核 Image 内定位 BTF（magic `0x9feb`，`__start_BTF`/`__stop_BTF`），解析结构体；
4. 反汇编取 `kmem_cache_alloc*` 调用的**返回地址**作为 site：
   | 对象 | 函数 | call | site |
   |---|---|---|---|
   | `k_itimer` | `do_timer_create` | +0xa4 | **do_timer_create+0xa8 = `0xffffffc008194fe0`** |
   | `cred` | `prepare_creds` | +0x1c | **prepare_creds+0x20 = `0xffffffc0080e5e70`** |
   | `task_struct` | `dup_task_struct` | +0x50 | **dup_task_struct+0x54 = `0xffffffc0080acb7c`** |
5. 其余符号直接来自 kallsyms（`selinux_state`、`panic_on_warn`、`kdp_enable`、
   `system_unbound_wq`、`call_usermodehelper_exec_work`、`anon_pipe_buf_ops`、
   `creds_fast_hash`、`root_user`、`init_user_ns`、`init_ucounts`、`init_groups`）。

> 用同一套脚本对 EZIF 内核重算，得到 `…193940 / …0e5308 / …0ac27c`，
> 与现有 EZIF 载荷里的常量**完全一致**，说明方法没有偏差。

完整常量表见 `app/src/main/cpp/targets/S721BXXUEEZIF/offsets.h`。

## 产物

- `app/src/main/assets/samtimer/targets/S721BXXUEEZIF/libtimer`
  （static aarch64 ELF，NDK r29 / API 34，2,337,456 B）
- 复用公共 `samtimer/uhexec`、`umh_action*.sh`、`ksud-32525`

## 待验证清单

- [ ] alpha 包能否在该机型上 staging 并跑通 `libtimer umhpipe`
- [ ] `KERNELSU_LIVE` 与 `su` 是否正常
- [ ] 需确认 `CRED_ALLOC_KDP`（0xd0，KDP 专用 cred slab）是否与该内核一致
      —— 该值来自 EZIF 经验，若不符会在 KDP 凭据安装阶段打印失败
- [ ] 失败时抓运行日志编号，比较 stage 输出定位是哪个常量偏了

## 实测记录

### alpha.331（2026-11-22，issue #49）

用户实测日志（本地编号 `99245289`）：

- `[+] pid=... [base] made=512 best=0xffffff7eb8066000 count=31` → UAF 触发正常
- `[+] plant1 ... marker pfn verified=0x91d9f6` → 物理 plant / marker 校验正常
- `[+] umh wq=0x9b3aee3c00000000` → **读回垃圾**
- `[!] umh bad wq -- parking without reap`

结论：per-build 符号/site 定标是对的（UAF、slide、pipe、plant 全过），错在
**内核镜像物理基址**：公共载荷假设 `phys = KERNEL_PHYS_LOAD + slide + (sym - KERNEL_VA_BASE)`
（e3q 成立），r12s 不成立。

修法：新增 `-DRMS_PHYS_SCAN=1`，运行时扫描 2MB 对齐的物理候选，用
`wq → dfl_pwq → pool → worklist` 校验确定 `g_kphys_base`，之后统一用
`static_phys()` / `runtime_phys()` 换算。

### alpha.3

待实测。
