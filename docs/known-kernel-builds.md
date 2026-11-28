# 内核构建号 ≠ 内核身份（EZK1 事故）

## 结论

`uname -r` 里的 `-<build>-` 数字**不能唯一标识内核**。三星会在不改变这个数字的情况下重编内核：

| 固件 | `uname -r` | 构建时间（/proc/version） | 98260 可用？ |
|---|---|---|---|
| S928BXXU6EZIF | `6.1.162-android14-11-34343818-abS928BXXU6EZIF` | **Sep 19 08:12 UTC 2026** | ✅ 已实测 |
| S9280ZHS6EZJ1 | `6.1.162-android14-11-34343818-abS9280ZHS6EZJ1` | **Oct 21 07:29 UTC 2026** | ✅ 已实测 |
| S928BXXS6EZK1 | `6.1.162-android14-11-34343818-abS928BXXS6EZK1` | **Nov  9 00:05 UTC 2026** | ❌ 实测 4 秒重启 |

三者的版本串只有机型后缀不同、构建号完全相同，但 EZK1 是 11 月重编的内核；
从行为看大概率 backport 了 posix-cpu-timers 的修复，98260 载荷打上去会硬重启。

## App 的处理

`RootViewModel` 里加了两道闸门（`coversBuild`）：

1. **构建码黑名单** `KNOWN_BAD_BUILD_CODES = {"EZK1"}` —— 从 `getprop` 拿，任何情况下都生效
2. **内核构建日期黑名单** `KNOWN_BAD_KERNEL_BUILD_DATES = {"2026-11-09"}` ——
   日期从 shell 侧 `cat /proc/version` 解析（app 域读不到该文件，走 `shellExecutor`），
   在机型页 `refreshKernelBuildDate()` 刷新

任一命中 → 98260 条目对该设备不可选，机型页顶部显示红色提示：
「当前内核构建（…）已知无法使用 98260：EZK1 实测 4 秒重启，请勿重试」。

机型页同时会显示 `Kernel build 2026-10-21` 这样的日期，便于用户反馈时直接对照。

## 以后加新固件

- 同一个构建号出现**新的构建日期**时，先当作「未知内核」：不要直接复用旧载荷
- 拿到该固件的 `boot.img` 后做 vmlinux diff（`do_timer_create`、`posix_cpu_timer_del` …），
  确认 98260 是否还在，再决定定标还是标为不支持
- 若确认不可用，把构建码加进 `KNOWN_BAD_BUILD_CODES`，把日期加进 `KNOWN_BAD_KERNEL_BUILD_DATES`
