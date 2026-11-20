# 社区方案：DZJ3 的 KernelSU 3.3.1 KDP（实验）

> 第三方项目：<https://github.com/Reallyliked21/dzj3-ksu-331-kdp>
> 本页只做**评估记录**，不是官方支持路径。官方默认仍是 3.2.5（`ksud-32525`）。

## 它解决什么问题

我们 App 给 DZJ3（SM-S9280 国行，内核 `6.1.145-android14-11-3254743-abS9280ZCS6DZJ3`）用的是
`ksud-32525` + `--ephemeral`：**不跑 module stage**，所以 LSPosed / Zygisk 模块在 late-load 后不会自动加载。
而这个社区版提供 KernelSU **v3.3.1 + Samsung KDP/RKP/DEFEX 补丁**，并改软重启为
「重跑 module stage + 重启 zygote」，**不真重启**就能让模块生效。

## 产物与校验（2026-11-20 实测下载核对）

Release `v3.3.1-dzj3-kdp-0.1`：

| 文件 | 大小 | SHA-256（自行下载后计算，与仓库 README 一致） |
|---|---|---|
| `kernelsu-331-kdp-dzj3.ko` | 430,856 B | `e5228933d940bbc07cfdd3aaf741f3ea2e70cecf801aa52195f3ad22d33d4bf3` |
| `ksud-331-kdp-dzj3` | 5,177,384 B | `6626e4d6da60c984b30e31c6440229831edc052e175e1477494122e9b7635c9e` |
| `ksud-331-kdp-dzj3-modsoft` | 5,164,448 B | `eb96471cbaacc15cb3faa82e1fd9455b3c7f87c22baf0ea1e5ff4c6f1df872cb` |
| `SHA256SUMS.txt` | 375 B | `f7659cd08b4f81974cd0ee9256c050ffcbba9dd038944a08d9247fd1706b0d05` |

> 校验只能证明「下载到的文件 == README 声称的文件」，**不证明二进制安全**。
> 要用的话，建议照仓库的 workflow 自己从 KernelSU v3.3.1 + 补丁构建，而不是直接用预编译件。

## 构建方式（仓库里的 `.github/workflows/build.yml`）

1. `git clone --branch v3.3.1 https://github.com/tiann/KernelSU.git`
2. 打 `patches/0001-samsung-kdp.patch`（KDP/RKP/DEFEX 兼容层，40 KB，改 `kernel/Kbuild`、`hook/*`、`userspace/ksud/src/late_load.rs` 等）
3. 用 DDK 镜像 `ghcr.io/ylarod/ddk-min:android14-6.1-20260313` 编 LKM，并把 `.ko` 内嵌进 ksud
4. 可选打 `patches/0002-soft-reboot-module-effective.patch`（把 `soft_reboot()` 从 `stop/start` 改成「重跑 post-fs-data + service + `setprop ctl.restart zygote`」）

## 已知限制（社区 README 自述 + 我们的判断）

- 仅 DZJ3 一个构建；换构建号要重新定 vermagic
- 实验版，长期稳定性未验证；KDP 上官方 `stop/start` 软重启会变真重启，管理器里的软重启按钮**别点**
- ADB Root 不支持（-38）；「隐藏 SELinux 修改」实际无效
- 管理器需配官方 **KernelSU v3.3.1**（驱动 32680），和我们的 3.2.5 管理器不通用
- 许可：KernelSU GPL-3.0、BakaSU 三星补丁 MIT；我们若要内置，需要保留 LICENSE/NOTICE

## 若要并入 RootMyS24，可选三条路

| 方案 | 做法 | 代价 / 风险 |
|---|---|---|
| A. 仅文档收录 | 像本页一样记录，不内置 | 用户要自己找、自己刷；但零风险 |
| B. 内置进 DZJ3 target | 把 ksud 放进 `assets/samtimer/targets/S9280ZCS6DZJ3/`，DZJ3 条目多一个「3.3.1 KDP」驱动选项 | APK 全量包 +5 MB；需 DZJ3 真机验证；第三方二进制合规与信任问题 |
| C. 服务端下发 | 上传到服务器 `dl/`，`payloads.json` 加一条带 sha256 的 ksud 选项，App 按需下载 | 不增 APK 体积；仍然只对官方包生效；需要真机验证 |

无论哪条，都**必须先有 DZJ3 真机验证**（我们手上是 EZJ1，跑不了这个构建）。
