# S721BXXUEEZIF target build（SM-S721B / S24 FE / r12s，**未验证**）

目标：`S721BXXUEEZIF`（Galaxy S24 FE，Exynos 2400e，One UI 9.0，kernel `6.1.162-android14-11`）。

## 来源

- 内核镜像：用户提供的 `boot.img`（AP 固件 `S721BXXUEEZIF`，built 2026-09-19）
- 载荷：`app/src/main/cpp/timer_samtimer.c`（EZIF/EZJ1 版），替换 16 个 per-build 常量
- 定标表：见同目录 `offsets.h`；方法在文件头注释里，**已在 EZIF 上回放验证**
- 结构体布局：与 EZIF（6.1.162/e3q）逐字段 diff，**完全一致**（BTF）

## 编译（NDK r29，与公共载荷相同）

```sh
NDK=/path/to/android-ndk-r29
$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android34-clang \
  -O2 -g -static -pthread -o libtimer timer_r12s.c
```

产物：`app/src/main/assets/samtimer/targets/S721BXXUEEZIF/libtimer`
（static ELF aarch64，NDK r29，API 34，含 debug_info）

## 运行时

`SamTimerUserService.openAsset()` 会优先读 `samtimer/targets/<构建号>/libtimer`，
所以设备构建号 `S721BXXUEEZIF` 自动命中该载荷；uhexec / ksud 仍用公共资产。

## 状态

- ⚠️ **未真机验证**：常量来自静态分析（vmlinux kallsyms + BTF + 反汇编）
- 风险：若某个常量不匹配，exploit 可能在 plant 阶段失败；失败时 payload 会
  `parking without reap` 保护（不要强杀），需要真重启
- 验证通过后应把 `docs/port-98260-S721BXXUEEZIF.md` 与 README 设备表更新为实测
