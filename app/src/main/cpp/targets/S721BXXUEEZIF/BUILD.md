# S721BXXUEEZIF target build（SM-S721B / S24 FE / r12s）

目标：`S721BXXUEEZIF`（Galaxy S24 FE，Exynos 2400e，One UI 9.0，kernel `6.1.162-android14-11`）。

## 来源

- 内核镜像：用户提供的 `boot.img`（AP 固件 `S721BXXUEEZIF`，built 2026-09-19）
- 载荷：`app/src/main/cpp/timer_samtimer.c`（公共版本），替换 16 个 per-build 常量
- 定标表：见同目录 `offsets.h`；方法在文件头注释里，**已在 EZIF 上回放验证**
- 结构体布局：与 EZIF（6.1.162/e3q）逐字段 diff，**完全一致**（BTF）

## 编译（NDK r29）

```sh
NDK=/path/to/android-ndk-r29
$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android34-clang \
  -O2 -g -static -pthread -DRMS_PHYS_SCAN=1 -o libtimer timer_r12s.c
```

`-DRMS_PHYS_SCAN=1` 是 r12s 专用：**内核镜像物理基址在 r12s 上不等于
`KERNEL_PHYS_LOAD + slide`**。首次实测（alpha.331）里 UAF/plant/marker 全过，但
`SYSTEM_UNBOUND_WQ_STATIC` 的读回值是垃圾（`umh wq=0x9b3aee3c00000000`），说明
e3q 的物理地址假设在这里不成立。

`RMS_PHYS_SCAN` 改为在 umh bootstrap 之后扫描 2MB 对齐的物理候选地址，
用 `wq -> dfl_pwq -> pool -> worklist` 四段校验确定真实基址，并写入 `g_kphys_base`；
之后所有静态符号走 `phys = g_kphys_base + (sym - KERNEL_VA_BASE)`，
kernel-image 的运行时 VA 走 `runtime_phys()`。公共载荷（e3q/EZIF）默认
`RMS_PHYS_SCAN=0`，行为不变。

产物：`app/src/main/assets/samtimer/targets/S721BXXUEEZIF/libtimer`
（static ELF aarch64，NDK r29，API 34，含 debug_info，2,344,264 B）

## 状态

- alpha.331 首次实测：UAF 触发、KASLR slide、pipe 泄漏、plant、marker 校验全部通过；
  卡在 UMH workqueue 读取（物理基址假设错误）→ 本版加 `RMS_PHYS_SCAN`
- ⚠️ 仍未真机验证成功：需要 alpha.3 实测
- 失败时 payload 会 `parking without reap`（不要强杀），一次开机只能试一次
