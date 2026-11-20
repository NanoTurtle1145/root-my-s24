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

## 已并入（方案 B，2026-11-20）

- 补丁与构建流水线已进仓库：`tools/ksu-kdp/patches/` + `.github/workflows/ksu-kdp.yml`
  （DDK 编 LKM，按目标设置 vermagic，补 `module_layout` CRC `0xea759d7f`，内嵌进 ksud）
- App 侧资产：`app/src/main/assets/samtimer/ksud-331-kdp`（一份，KMI `android14-6.1` 通用）
- 机型页「KSU 驱动版本」新增 **3.3.1 KDP（实验）** 选项，仅当资产存在时出现
- 3.3.x 走 stock action（会跑 module stage），3.2.5 仍走 `--ephemeral`
- 配套管理器：官方 **KernelSU v3.3.1**（驱动 32680）

> One UI 9（EZIF/EZJ1/EZJB/EZJD）与 DZJ3 都是 `android14-6.1` KMI；
> KernelSU v3.3.1 的 late-load 用用户态 `init_module` 加载，vermagic 不匹配时会
> 从内核日志取目标 vermagic 改写后重试，所以同一份 ksud 可跨构建号使用。
> **已真机验证**：One UI 9（EZJ1）加载成功，详见文末记录；模块仍需 zygote 重启。

## 其它可选路（原评估）

| 方案 | 做法 | 代价 / 风险 |
|---|---|---|
| A. 仅文档收录 | 像本页一样记录，不内置 | 用户要自己找、自己刷；但零风险 |
| B. 内置进 DZJ3 target | 把 ksud 放进 `assets/samtimer/targets/S9280ZCS6DZJ3/`，DZJ3 条目多一个「3.3.1 KDP」驱动选项 | APK 全量包 +5 MB；需 DZJ3 真机验证；第三方二进制合规与信任问题 |
| C. 服务端下发 | 上传到服务器 `dl/`，`payloads.json` 加一条带 sha256 的 ksud 选项，App 按需下载 | 不增 APK 体积；仍然只对官方包生效；需要真机验证 |

无论哪条，都**必须先有 DZJ3 真机验证**（我们手上是 EZJ1，跑不了这个构建）。

## 真机验证记录（2026-11-20，SM-S9280 / S9280ZHS6EZJ1 / One UI 9）

跑 `v3.6.4-beta.317-s24`，KSU 驱动选 **3.3.1 KDP**：

| 项 | 结果 |
|---|---|
| exploit | 成功（`KERNELSU_LIVE`，98260 链路正常） |
| 内核模块 | `kernelsu 233472 0 - Live` 已加载 |
| ksud | `ksud 3.3.1 (uapi: 5)`，`/system/bin/su` 可用（`u:r:ksu:s0`） |
| KDP 兼容 | dmesg `Samsung KDP task-scoped credential install` 正常 |
| 版本替换 | LKM 按 EZJ1 的 `-abS9280ZHS6EZJ1` 构建，跨构建号加载路径跑通 |
| **模块 stage** | **未生效**：zygote 在 boot 时已启动，late-load 的 stage 不重开 zygote；QQ/system_server 的 maps 里没有 `zygisk_vector`/`lspd` |
| 管理器 | 机上还是 **v3.3.0 (32601)**，与 3.3.1 驱动（32680）不匹配，需换官方 **v3.3.1** |

> 模块补齐要一次「module-effective 软重启」（`ksud soft-reboot`：重跑 stage + `ctl.restart zygote`）。
> 98260 的 parked 进程会被这次框架重启杀掉，有硬重启风险（v3.6.2 的安全拆除能降但不能归零）。

### 2026-11-20 现场复核：那不是内核重启，是框架重启

一开始按「硬重启」记录，后续复核推翻了：

| 证据 | 数值 |
|---|---|
| 内核 uptime | **891 s 连续**（boot 于 21:34，事件发生在 21:37 之后） |
| `zygote64` / `system_server` ETIME | **11:15 / 11:14** → 只有这两个重启了 |
| `/proc/modules` | `kernelsu` 仍在（LKM 在内核内存，框架重启不会卸载） |
| QQ 的 `/proc/pid/maps` | `zygisk_vector` / `zygisksu` 命中 **6 处** → 模块确实注入了 |
| dmesg | 未见 panic / Oops（98260 的 parked 被杀但没有出事，v3.6.2 安全拆除生效） |

结论：
- 管理器那个「软重启」实际执行的是 **module-effective 路径**（重跑 stage + `ctl.restart zygote`），
  不是内核官方 `stop`/`start`——否则 KDP 上会真重启；
- 屏幕上看起来像"重启"（黑屏、桌面重建、adb 掉线）是**框架重启**，内核从未重启；
- **这正是我们想要的结果**：root 还在、模块也加载了、没有硬重启；
- 代价只有：Shizuku 侧 UserService / parked 进程被杀，App 里 `state.rooted` 变 false，
  但 KSU 驱动与管理器仍然工作，直到下一次真重启。

### App 内提供的软重启入口（v3.6.4-beta.318+）

管理器按钮不可用，但 App 里补了一个正确入口：

**运行日志页 → 跑完后从底部浮出「软重启（加载模块）」按钮**

- 只有 `state.rooted` 且当前驱动是 3.3.x 时才出现（3.2.5 的 ksud 没有这个补丁）
- 点击 → 二次确认（说明会杀 parked、有硬重启风险）→ 通过 UserService 执行
  `su -c '/data/adb/ksud soft-reboot'`
- 执行输出实时追加到日志页，方便判断成功/失败

> 按钮的显示/收起用 `AnimatedVisibility`（slideInVertically + fadeIn），
> 遵循 `docs/UI-GUIDELINES.md` 的圆角与间距规范。

## 官方 KernelSU 的「软重启」到底做了什么

`userspace/ksud/src/soft_reboot.rs`（v3.3.1）里的 `soft_reboot()`：

1. `ensure_uapi_version_matched()` —— 管理器内置 ksud 与内核 uapi 不匹配时**直接跳过**；
2. `daemonize_with(true, switch_mnt_ns(1))` —— 切到 init 的 mount namespace；
3. `reset_boot_completed()` —— 把 `sys.boot_completed` 置 0；
4. `run_stage("emulated-soft-reboot")` —— 跑模块的模拟软重启脚本；
5. 起内嵌的 `waitsys` 辅助进程，观察 service 状态；
6. **`stop`** → `on_post_fs_data()` → **`start`** → `on_services()` → 等 `sys.boot_completed` → `on_boot_completed()`；
7. `_exit(0)`。

关键点：

- `stop`/`start` 是 **init 的框架启停**，**内核不重启**；
- KernelSU 的 LKM 在内核内存里，框架重启**不会卸载它** → 所以「软重启后 KSU 还在」是**设计行为**，不是 KDP 特例；
- 官方代码**没有任何 KDP 分支**；KDP 适配来自社区补丁 `0002`，它把第 6 步换成
  「重跑 post-fs-data/service + `setprop ctl.restart zygote`」，避免某些三星构建上 `stop`/`start` 变成真重启。

## 两个入口跑的不是同一份 ksud

| 入口 | 实际执行 | 是否含 0002 补丁 |
|---|---|---|
| KSU 管理器「软重启」按钮 | 管理器 APK 内置的 `libksud.so soft-reboot`（`KsuCli.getKsuDaemonPath()`） | ❌ 官方版 |
| 本 App 日志页「软重启（加载模块）」按钮 | `/data/adb/ksud soft-reboot`（由我们的 ksud-331-kdp 安装） | ✅ 含补丁 |

所以：**管理器按钮永远走官方 stop/start**（哪怕管理器升到 3.3.1），
要稳定拿到 module-effective 行为，用 App 里那个按钮（或命令行 `/data/adb/ksud soft-reboot`）。

区分两条路径看 logcat：

- 官方：`emulating soft_reboot!` → `stop` → `start`
- 补丁：`module-effective soft reboot: post-fs-data / service / restart zygote`
