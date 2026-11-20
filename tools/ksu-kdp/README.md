# ksu-kdp：KernelSU 3.3.1 + Samsung KDP 补丁（实验）

给「需要 module stage / 软重启不真重启」的机型用的 3.3.x 驱动。

## 补丁来源

`patches/` 下的两个补丁来自社区项目
[Reallyliked21/dzj3-ksu-331-kdp](https://github.com/Reallyliked21/dzj3-ksu-331-kdp)：

| 文件 | 作用 |
|---|---|
| `0001-samsung-kdp.patch` | KDP/RKP/DEFEX 兼容层（kprobe hook `task_defex_enforce`、`no_patch_text`、late-load staging 等） |
| `0002-soft-reboot-module-effective.patch` | `soft_reboot()` 改成「重跑 post-fs-data + service + 重启 zygote」，KDP 上不真重启 |

许可：KernelSU GPL-3.0；补丁上游自述源自 BakaSU 的三星补丁（MIT）。分发时保留 `LICENSE` 与本节说明。

## 构建

`.github/workflows/ksu-kdp.yml`（手动 dispatch）：

1. 拉 KernelSU `v3.3.1`；
2. 打两个补丁；
3. DDK 镜像 `ghcr.io/ylarod/ddk-min:android14-6.1-20260313` 编 LKM，
   `kernel.release`/`UTS_RELEASE` 按目标设置，并给 `__versions` 补 `module_layout` CRC `0xea759d7f`；
4. LKM 内嵌进 ksud，cargo 交叉编 aarch64；
5. 产物：`lkm-<target>` / `ksud-<target>`。

## 为什么只需要一份 ksud

KernelSU v3.3.1 的 late-load 走 `ksuinit::load_module()`（用户态重定位 + `init_module`），
vermagic 不匹配时会**从内核日志取出目标 vermagic、改写内存里的模块再重试**；
`module_layout` 的 CRC 已在构建时补成 Samsung 内核的值。
因此同一 KMI（`android14-6.1`）的构建（DZJ3 / One UI 9）可以共用一份 ksud。

App 侧对应资产：`app/src/main/assets/samtimer/ksud-331-kdp`（`ksud-331-kdp` 选项）。

## 配套管理器

官方 **KernelSU v3.3.1**（驱动 32680）。和 3.2.5 / 3.3.0 管理器不通用。

## 风险

- 实验功能：加载的是自编内核模块，失败可能 panic/重启
- DZJ3 之外（One UI 9）尚未真机验证前，不要当默认驱动
