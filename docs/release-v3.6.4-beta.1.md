# v3.6.4-beta.1（预发布）

这是 **v3.6.4 的预发布**，给愿意帮忙验证的用户用，不是正式版。

## 0x00 请重点验证两件事

1. **issue #35：「禁用 KSU 模块」在 98260 路径生效了吗？**
   用那个会导致 panic 的 eSIM 模块（或自建测试模块，service.sh 写文件）：
   - 打开「禁用 KSU 模块」→ 用 98260 + 3.2.5 跑一次
   - 日志里应出现 `module disabled: /data/adb/modules/<id>`
   - 模块的 service.sh **不应该**被执行
   - 验证完记得在 KSU 管理器里手动重新启用模块（disable 标记是持久的）

2. **DZJ3 target（PR #44 / issue #40）**
   SM-S9280 国行 `S9280ZCS6DZJ3`（6.1.145）：
   - 机型页应该能看到 DZJ3 条目（`tested=false`，需在设置里打开「启用未经测试的载荷」）
   - 跑一次，把运行日志编号发到 issue #40
   - 成功后会把 `tested` 改成 `true`

## 0x01 本版改动

- **98260 per-target 载荷支持**：`samtimer/targets/<TAG>/libtimer` 优先于公共载荷（AIDL `setTargetTag`）
- **第一个 per-target 目标：S9280ZCS6DZJ3**（社区 Reallyliked21 定标，手动 staging 真机验证通过，App 内待验证）
- **#35 修复**：禁用 KSU 模块改为给模块目录写持久 `disable` 标记（不再 mv/恢复），日志按驱动版本区分（3.3.0 不会再谎称 ephemeral）
- EZJ1 实测确认：`S928BXXS6EZJ1` 用 98260 + 3.2.5 可用（加进机型表）
- CONTRIBUTING 补 98260 per-target 适配流程；issue 模板要求日志编号

## 0x02 注意

- `versionName` 会带 `-beta.<versionCode>`，正式版仍是裸版本号
- 98260 依旧：一次开机只跑一次；3.3.0 驱动在 6.1.162 上不稳，别用
- 遇到问题请贴运行日志编号（运行日志页 →「复制编号」）

NanoTurtle,2026/11/14
