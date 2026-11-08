# v3.6.2

这一版围绕 98260 引擎的稳定性：把"软重启会硬重启"这条坑填掉，并把"一次开机一次尝试"做成硬规则。

## 0x00 安全拆除（safe unlink）

98260 成功后会 parked，进程手里拿着 victim 的 stale `timerqueue_node`。之前只要发生软重启
（parked 进程被 system 杀掉），init 接手 reap zombie，`cleanup_posix_cputimers()` 走到
已释放页上的节点，内核直接 panic → 你看到的"硬重启"。

现在 parked 之前会：
1. 记录每个 held victim 的 `k_itimer`
2. 用 exploit 自己的 physrw 读 `it_signal`，把 victim `signal_struct` 里 **3 个 CPU-clock
   的 timerqueue 树根**（rb_root + rb_leftmost）清零
3. 之后即使 parked 进程死掉、zombie 被 reap，遍历的是空树，不会再二次摘链

偏移是从 EZIF 6.1.162 内核 BTF 校准的，不是猜的：

| 字段 | 偏移 |
|---|---|
| `k_itimer.it_signal` | 0x60 |
| `signal_struct.posix_cputimers` | 0x120 |
| `posix_cputimer_base` stride / `tqhead` | 0x18 / +0x8 |
| 三个树根 | sig + 0x128 / 0x140 / 0x158 |

读之前有范围校验，偏移不对就跳过、不写内存。

## 0x01 一次开机只跑一次（强制）

实测坑：同一个开机会话里连跑几次 98260，内核状态变脏后 exploit 会在 plant 中途直接 panic
（日志断在 plant1，连 parking 都没到）。现在 App 会记录本开机的尝试结果：
- 同一开机已尝试过且没成功 → 直接拦住，提示"请先重启"
- 成功则放行，不再重复提示

## 0x02 3.2.5 路径确认可用

实机确认（SM-S9280 EZIF）：
- 3.2.5 + ephemeral：root 正常
- 软重启不再硬重启
- 软重启后 Vector / Zygisk 模块能正常加载

EZIF 默认仍是 3.2.5。

## 0x03 3.3.0

3.3.0 现在只做一件事：用假 `am` 挡掉 ksud 的"自动打开管理器"。
**不再移走 `/data/adb/modules`** —— 上一版那么做会跳过 KSU 的 module stage，
Vector/Zygisk 在本开机内永远不加载（3.2.5 路径正常、3.3.0 不正常的根因）。

代价：3.3.0 的 late-load 会正常跑 stage，Vector/Zygisk 自己重启 system_server（一次框架重启）。
3.3.0 仍标为实验档：它的 stock 多 KMI 模块在 EZIF 上偶发 system_server 崩溃循环 → RescueParty → 整机重启。

## 0x04 其他

- 引擎在日志/主页统一叫「98260 UAF」（SamTimer 只是实现参考，不是漏洞名）
- SM-S921E DZIF 加入机型表（issue #33 实测 98260 + 3.2.5）

NanoTurtle,2026/11/08
