# 贡献指南

感谢愿意帮忙。这个项目最需要的是**机型/固件适配**，其次是带日志的失败报告。

---

## 报告问题

请直接用仓库里的 **issue 模板**（Bug / 成功反馈 / 功能建议）。模板已经列出需要的信息，其中这几项是排查的前提：

- 机型完整型号（`SM-S9280` 这种）
- 系统版本 / One UI 版本
- **完整内核版本串** —— `adb shell uname -r`，要一整行，形如
  `6.1.145-android14-11-3254743-abS9280ZCS6DZH3`。**不要只给尾部的构建号**，
  同一个构建号在不同平台上偏移都可能不同。
- 内部版本号（设置 → 关于手机 → 软件信息，或 `getprop ro.build.display.id`）
- App 版本 + 引擎（GhostLock 43499 / 98260 UAF）+ KSU 驱动版本（3.2.5 / 3.3.0）
- **运行日志编号**（v3.6.3 起：运行日志页 →「复制编号」），或直接贴完整日志
- 失败发生在哪一步（日志里最后一个 `[*]` / `[-]` 行）

知道这些之后，大多数问题能一眼定位，例如：

> 内核构建号 `33419968` 与已适配的 `3254743` 不同 → 载荷不能复用，需要独立定标。

> 98260 引擎请务必说明是「一次开机第几次尝试」：同一开机重复尝试会让内核状态变脏，日志会断在 plant 中途。

---

## 机型适配（最有价值的贡献）

本项目有两套引擎，载荷结构不同，适配流程也不同：

| 引擎 | 适用 | 载荷位置 | 定标依据 |
|---|---|---|---|
| **GhostLock 43499** | 旧固件（6.1.145 及更早） | `app/src/main/assets/cve-2026-43499-<构建号>` | 符号表 + p0 指纹 |
| **98260 UAF** | One UI 9.0 / 6.1.162 等新固件 | `app/src/main/assets/samtimer/targets/<TAG>/libtimer` | 符号表 + BTF + 调用点反汇编 |

### 43499 载荷适配

同 SoC 系列也**不能**直接套用别人的载荷 —— 内核构建号不同、甚至构建号相同
而平台不同时，符号偏移都可能不一样（e3q 与 e1q 同为
`…-android14-11-3254743-…`，实测有 11 项偏移不同）。所以需要逐项定标。

大致流程（参考 `docs/port-e1q-S9210ZCS6DZI1.md` 的完整实例）：

1. 从官方固件包取出 `boot.img` → 内核段（注意 boot header 版本与内核段偏移）
2. 用 `vmlinux-to-elf` 恢复符号
3. 逐项定标 `target.h` 里的偏移（`llvm-nm` + 必要时的指针扫描）
4. 确认 `P0_KERNEL_PHYS_LOAD` —— 这一项经常需要按机型改
   （例如 `0x80080000` 会恒定卡在 `p0 physical write status=256 ok=0`，
   改用 `0xa8000000` 才通过）
5. 生成 p0 指纹表
6. 编译载荷
7. **真机验证**

### 98260 载荷适配（per-target，issue #40 起）

98260 的公共载荷是 `samtimer/libtimer`（覆盖 EZIF/EZJ1）。**只加构建号专属载荷，不改公共载荷**：

```
app/src/main/assets/samtimer/targets/<TAG>/libtimer   # 必需，按构建号编译的 exploit 载荷
app/src/main/assets/samtimer/targets/<TAG>/uhexec     # 可选，与公共版本不同才放
app/src/main/assets/samtimer/targets/<TAG>/ksud       # 可选，公共 3.2.5 驱动加载不了才放
```

- `<TAG>` = **完整构建号大写**（如 `S9280ZCS6DZJ3`，不要用 `DZJ3` 这种四位缩写）
- 运行时选择已经实现：`samtimer/targets/<设备构建号>/` 存在就用它，否则回退公共载荷
- 源码放 `app/src/main/cpp/targets/<TAG>/`（offsets 头文件 + 构建说明），PR 里附：
  - `libtimer` 的 **SHA-256** 与体积
  - 编译命令（NDK 版本 + flags）
  - 定标记录（符号来源、BTF 校验了哪些结构、失败签名）

98260 定标至少需要覆盖：

1. `do_timer_create()` 里 `kmem_cache_alloc(posix_timers_cache, …)` 的**返回指令地址**（`*_STATIC` / `*_LOW21`），用于 tracefs 记录匹配
2. `prepare_creds()` / `dup_task_struct()` 的 cred/task 分配点
3. 静态符号：`selinux_state`、`panic_on_warn`、`system_unbound_wq`、`call_usermodehelper_exec_work`、`root_user`、`init_user_ns`、`anon_pipe_buf_ops` 等
4. 结构偏移（BTF 校验）：`k_itimer`、`signal_struct.posix_cputimers`、`workqueue_struct` / `pool_workqueue` / `pool`、`pipe_buf_ops`、`cred`、`task_struct`
5. `KERNEL_PHYS_LOAD` 与 `WQ_DFL_PWQ_OFF` 这类与内核线相关的常量（6.1.145 与 6.1.162 不同）

> 同一个内核构建号（`6.1.145-android14-11-<build>-ab<BUILD>` 里 `<build>` 相同）才能复用偏移；
> 平台不同（e3q / e1q）即使构建号相同也要逐项核对。

### 提交 PR 时的硬性要求

| 要求 | 原因 |
|---|---|
| **真机验证过，并写明验证条件** | 未验证的载荷合进去，用户会拿它去刷真机 |
| 附载荷的 **sha256 与体积** | 便于核对，避免二进制被替换 |
| 附**完整偏移对照表**（与本项目已有平台比） | 后人适配时能看出规律 |
| 98260 载荷附 **BTF / 反汇编校验证据** | 结构偏移错了不是"跑不起来"，是直接 panic |
| 附**失败签名**（你踩过的坑） | 以后一眼分辨「载荷不匹配」与「race 没跑起来」 |
| 新增/修改的载荷**不得包含**任何外联、下载、执行第三方代码的行为 | 这是提权工具，用户信任成本极高 |
| 第三方 ksud / 模块资产注明来源与许可（MIT / Apache-2.0 / GPL） | 本仓库 GPL-3.0，需要能再分发 |

PR 里通常需要改这几处：

```
# GhostLock 43499
app/src/main/assets/cve-2026-43499-<构建号>     # 载荷
app/src/main/kotlin/.../RootViewModel.kt        # FirmwareVersion 新增一条
server/payloads.json                            # 在线清单新增一条
docs/port-<平台>-<构建号>.md                     # 移植报告

# 98260 UAF
app/src/main/assets/samtimer/targets/<TAG>/libtimer
app/src/main/cpp/targets/<TAG>/                 # offsets 头 + 构建说明
docs/port-98260-<TAG>.md                        # 定标记录
```

新条目默认请标 `tested = false`，等真机跑通再改 `true`。

---

## 自行编译、自行发布（fork）

**可以 fork，但请先读 `AGENTS.md` 里「如果你是 fork 后自行编译」那一节。**
摘要：

1. **改掉统计上传地址**（`LogUploader.DEFAULT_ENDPOINT`、`PayloadStatus.ENDPOINT`），
   或设为 `""` 直接关掉上传。
2. **用你自己的签名 keystore**。服务端按签名区分官方与第三方，
   非官方签名会被记为 `third_party` 并**从统计中排除** ——
   所以漏改地址不会污染官方成功率，但仍会占用官方带宽。
3. **自制载荷不用改服务端清单**：非官方构建会自动跳过在线清单，只用内置的。
4. **别照抄别人的载荷**，理由见上文。

---

## 代码约定

- 从 `main` 切分支，PR 指向 `main`。
- commit 信息写清**为什么**改，而不只是改了什么；涉及取舍的请把取舍写出来。
- 注释请解释「为什么」，代码本身说明「是什么」。
- 新增用户可见文案时，同步补 `values-zh-rCN` / `values-zh-rTW` / `values-zh-rHK`
  （英文在 `values/`）。缺翻译会回落英文，不报错。
- 改动 App 行为后，请在真机上至少验证一次目标路径。
- 新增 AIDL 方法时同步改 `IRootService.aidl` 与 `SamTimerUserService` 的实现，并保持向后兼容（旧 UserService 进程可能还在跑）。

## 不会被合并的内容

- **未做真机验证**的新载荷（宁可不收）
- 把统计上传指向**第三方服务器**的改动
- 移除版权/来源说明、或与项目许可证冲突的代码
- 仅改版本号、空提交、纯格式化的噪音 PR

---

## 许可证

提交即表示你同意以本项目的许可证发布你的贡献。请**不要**提交来源不明、
无许可证或明确禁止再分发的代码（例如某些无 LICENSE 的仓库，
可以参考其思路，但不要复制实现）。

已知可再分发的参考实现：SamTimer（MIT）、Root-My-Galaxy-Payloads（Apache-2.0）、
KernelSU（GPL-2.0）；合入时在 `THIRD_PARTY_NOTICES.md` 保留对应声明。
