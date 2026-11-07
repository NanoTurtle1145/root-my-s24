# v3.6.0

这次版本号跳到 3.6.0，因为不是修修补补：加了第二个提权引擎。

## 0x00 新增：98260 UAF 引擎

除了原来的 GhostLock（CVE-2026-43499），现在多了一个引擎：

**CVE-2026-98260**（`exec: Cleanup POSIX timers right after de_thread()`，posix-cpu-timers UAF）。

- 机型选择页新增「提权引擎」选项卡：`GhostLock 43499` / `98260 UAF`
- **SM-S9280 `S9280ZHU6EZIF`（One UI 9.0 / 6.1.162）** 默认走 98260 UAF，真机已拿到 root
- 链路：timer UAF → pipe 读写 → 伪造 workqueue → `call_usermodehelper` → uhexec → late-load
- 不写 cred，不碰 KDP/RKP/DEFEX 的凭据保护
- exploit 跑在 Shizuku UserService 常驻进程里，App 切后台不影响

实现参考 [SamTimer v1.3](https://github.com/RiosWesley/SamTimer)，但资产、驱动、流程都按我们自己的方式
重新打包和定标（包内 ksud 是 3.2.5 ephemeral 补丁版）。

## 0x01 驱动：3.2.5 和 3.3.0 的区别

EZIF 默认 **3.2.5**：late-load 带 `--ephemeral`，只加载模块，不写 `/data/adb`、不跑 module stage，
所以**不会自动打开 KSU 管理器，也不会软重启**。

3.3.0 保留为实验选项：能 root，但它的 stock ksud 不支持 ephemeral，会 `am start` 管理器并软重启一次
（framework 重启，无害）。选了会出红色提示，别忽略。

## 0x02 其他改动

- 机型页的「KSU 驱动版本」「提权引擎」两个选项卡移进列表，随机型列表一起滚（以前固定占半屏）
- 主页载荷标签按实际引擎显示，不再把 EZIF 显示成 43499 载荷
- SamTimer 链路按原版经验处理 `parking without reap`：检测到就暂停，提示重启/重跑
- 隐私政策页（设置 → 隐私）/ 内置 Markdown 渲染

## 0x03 已知问题

- 98260 的 UAF 失败时只能重启清状态，同一开机别反复跑
- 3.3.0 的 DDK 构建（内嵌 DZH3 模块）在 EZIF 上加载即 panic，不要用
- EZIF 的 3.3.0 驱动无法 ephemeral：它触发的软重启会杀掉 98260 parked 的 exploit 进程，
  少了这个"拿着页"的进程就可能直接硬重启。**98260 引擎请用 3.2.5**（不软重启）

NanoTurtle,2026/11/07
