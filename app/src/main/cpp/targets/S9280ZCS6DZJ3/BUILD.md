# DZJ3 target build

目标：`S9280ZCS6DZJ3`（SM-S9280 CHC / e3q / 6.1.145）。

载荷来源：
- 基于 SamTimer 98260 链路，源码/完整构建说明：
  https://github.com/Reallyliked21/galaxy-s24u-dzj3-temp-root
- 本目录的 `offsets.h` 是 DZJ3 定标常量；payload 使用该头文件编译。

编译（NDK r29）：

```bash
export ANDROID_NDK_HOME=/path/to/android-ndk-r29
$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/\
aarch64-linux-android34-clang \
  -O2 -fPIC -shared -s -Wl,--build-id=none \
  -o libtimer timer-dzj3-apk.c
```

编译参数：
- NDK r29
- ABI arm64-v8a / API 34
- `-O2 -fPIC -shared -s -Wl,--build-id=none`

运行时兼容性（RootMyS24 `SamTimerUserService`）：
- 资产会被 stage 到 `/data/local/tmp/w/libtimer`；
- 以 `libtimer umhpipe` 启动；
- payload 自己创建 `/data/local/tmp/samtimer`，并从公共资产路径
  `/data/local/tmp/.ksud-stage` 复制 ksud；
- UMH 走 `/system/bin/sh` + payload 内置的 action script，不依赖公共
  `uhexec`/`umh_action.sh`；
- 因此 target 目录只需要 `libtimer`，不需要额外 `uhexec`/`ksud`。

载荷：
- 名称：`libtimer`
- 大小：2,228,648 B
- SHA-256：`8989511991779fa0eefa68bcde0373ff36737b228a617e9e2c0851edf5386539`


## 已验证

- 手动按 SamTimerUserService staging 到 `/data/local/tmp/w` 后，`libtimer umhpipe` 返回 `KSU_LOADED`，`kernelsu` live，`su` 为 uid 0。
