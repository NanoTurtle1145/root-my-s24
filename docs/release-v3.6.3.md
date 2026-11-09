# v3.6.3

这次主要是"反馈闭环"和 98260 路径的收尾。

## 0x00 运行日志：本地存档 + 日志编号

以前日志只存在内存里，App 一杀、一重启就没了，用户反馈时也拿不到任何能引用的东西（服务器那条链接只有我们自己看得到）。

现在每轮运行结束都会：
- 把本轮日志写到本机 `filesDir/runlogs/<时间戳>_<编号>.log`，保留最近 30 条
- 分配一个短编号（RUN-ID 前 8 位，如 `3fa9c1d2`）
- 日志里多一行：`◆ 本地日志编号: xxxxxxxx`

运行日志页新增：
- **「复制编号」**按钮
- **「本机历史日志(N)」**卡片：展开后列出最近 10 条（编号 / 时间 / 结果），可点开查看完整日志、复制编号

以后再收到反馈，贴编号就行，不用再要服务器链接。

## 0x01 「禁用 KSU 模块」在 98260 路径生效

issue #35：开关只在 GhostLock(43499) 路径实现，98260 路径没读，等于没开。

现在的做法：
- AIDL 增加 `setDisableModules(boolean)`，UserService 写标记文件
- action 脚本在 `ksud late-load` **之前**（UMH root 上下文）把 `/data/adb/modules`、`modules_update` 改名为 `*.rms24bak`，late-load 后恢复
- 文件不丢，只是本次会话不加载模块

## 0x02 3.2.5 路径最终确认

实机确认（SM-S9280 EZIF）：
- 3.2.5 + ephemeral：root 正常
- 软重启不再硬重启
- 软重启后 Vector / Zygisk 能正常加载

3.3.0 仍是实验档：stock 模块在 6.1.162 上偶发 system_server 崩溃循环 → RescueParty → 整机重启；98260 引擎不推荐。

## 0x03 其他

- README 重整（引擎优先、最新版本区、BakaSU 后移、驱动说明纠正）
- 新 banner（Swiss 21:9，2100×900）
- GitHub issue 模板：bug 报告要求填「日志编号」，成功反馈字段对齐机型表
- EZJ1（S9280ZHS6EZJ1 / S928BXXS6EZJ1）与 EZIF 同内核构建 `34343818`，用 98260 + 3.2.5 即可

NanoTurtle,2026/11/10
