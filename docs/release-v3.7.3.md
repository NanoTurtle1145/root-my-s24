# RootMyS24 v3.7.3

扩展 DZJ3 临时 root 到 **Galaxy S24 标准版（SM-S9210 / e1q）**。

## 背景

社区仓库 [mrlihx/galaxy-s24-e1q-dzj3-temp-root](https://github.com/mrlihx/galaxy-s24-e1q-dzj3-temp-root)
把 Reallyliked21 的 DZJ3 root 包移植到 e1q，并给出关键事实：

- `S9280ZCS6DZJ3`（e3q）与 `S9210ZCS6DZJ3`（e1q）**共用同一份内核镜像**
  （`6.1.145-android14-11-3254743`，changelist 与构建时间相同）；
  因此 `native/offsets.h` 的 59 个偏移常量一个都不用改。
- 原上游源码的 `build.sh` 用 `-shared` 链接 payload，产物是 ET_DYN、入口为 0，
  被 `ProcessBuilder` 直接 exec 时会 SIGSEGV；需要 `-pie -fPIE`。
  （RootMyS24 的 DZJ3 载荷是静态 EXEC，`Entry point 0x21F380`，不存在这个问题。）

## 变更

- 新增 per-target 载荷目录 `app/src/main/assets/samtimer/targets/S9210ZCS6DZJ3/libtimer`
  （与 S9280ZCS6DZJ3 同一份二进制：内核相同，offsets 相同）
- 机型条目 `S9280DZJ3` 的设备描述改为 **「S24 全系 · 国行（S9280 / S9210）DZJ3」**；
  覆盖范围仍按构建码 `DZJ3`，e1q/e3q 都会命中
- 运行时按**设备构建号**取 target 目录：S9210 设备命中 S9210ZCS6DZJ3，S9280 命中 S9280ZCS6DZJ3

## 使用

s24 包安装后，SM-S9210（S24 国行）与 SM-S9280（S24 Ultra 国行）的 DZJ3 固件都会
被自动检测为「S24 全系 · 国行 DZJ3」条目：引擎 98260 UAF、KSU 驱动 3.2.5。

## 验证

- e3q：RootMyS24 侧已有实测记录（PR #44 / v3.7.2）
- e1q：mrlihx 仓库已在 SM-S9210 真机完成 root + LSPosed 验证（同一内核、同一 offsets）
- RootMyS24 尚未在 e1q 真机上跑过自己的 APK；失败请反馈日志编号
