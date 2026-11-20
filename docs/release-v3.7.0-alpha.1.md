# RootMyS24 v3.7.0-alpha.1

> **alpha 渠道**：引擎/框架试验版，可能不稳定。正式使用请等 beta/stable。
> 本次重点：**新增 KernelSU 3.3.1 KDP 驱动（含 One UI 9）**、**机型页/设置页/关于页重构**、
> 运行日志页改造、环境隐藏增强。

## 新增

### KernelSU 3.3.1 KDP 驱动（实验）
- 机型页「KernelSU 驱动版本」新增 **3.3.1 KDP** 档位
- 官方 KernelSU v3.3.1 + 三星 KDP/RKP/DEFEX 兼容补丁，由本仓库 CI 自编（可复现）
  - LKM 按 android14-6.1 KMI 编译，`ksuinit` 用户态加载 + 运行时替换 vermagic
  - 同一份 ksud 可跨构建号使用：DZJ3（6.1.145-3254743）与 One UI 9（6.1.162-34343818）
- 配套管理器：官方 **KernelSU v3.3.1（32680）**
- **实测**：SM-S9280 / S9280ZHS6EZJ1（One UI 9）加载成功，`ksud 3.3.1 (uapi: 5)`、`su` 正常

### 环境隐藏
- 设置 → 高级与诊断 → **隐藏 su 兼容模式**：跑完 root 后关闭 `su_compat`，
  应用侧看不到 `/system/bin/su`（代价：adb/Termux 的 `su` 失效到重启）
- 顺带清理了旧版本遗留的 `/system/bin/logcat` 泄漏挂载

### 机型页重构
- 顶部新增**当前设备（自动检测）**卡：型号 · 构建号 · 内核串 · 推荐驱动/引擎/实测状态
- 98260 覆盖范围改为按**内核构建号**判定：EZIF / EZJ1 / EZJB / EZJD 自动覆盖
- 同载荷条目合并为 target 行（按引擎 + 载荷分组），需要时展开
- **选中后不再自动返回主页**（留在页面继续对比/改驱动）

### 运行日志页
- **历史日志**改为工具栏同级按钮，点击进入独立页面（列表 + 查看全文）
- **日志编号开跑即生成**，跑一半也能复制反馈
- 修复编号重复：原取 RUN-ID 前 8 位（同一安装所有运行相同）→ 改取尾部毫秒 8 位
- KSU 已加载且驱动为 3.3.x 时，底部浮出**软重启（加载模块）**按钮（走补丁版 `ksud soft-reboot`）

### 其它
- 设置页拆为「常用 / 高级与诊断」两页
- 关于页 FAQ 默认折叠
- 新增 nightly 渠道与 `.github/workflows/nightly.yml`（每日自动构建 + 滚动 pre-release）
- 新增 `docs/UI-GUIDELINES.md`（圆角/尺寸/间距规范，改界面前必读）

## 已知限制

- 3.3.1 KDP 为实验档：加载失败最坏会 panic/重启；模块生效依赖一次 zygote 重启
- 98260 一次开机只能跑一次；失败请真重启后再试
- 管理器按钮的软重启走官方 stop/start（框架重启，内核不重启）；App 内按钮走补丁版路径
- 3.3.0 驱动在 6.1.162 上仍不推荐（stock 模块偶发 system_server 崩溃循环）

## 校验

产物命名 `RootMyS24-v3.7.0-alpha.<commit>-build<code>[-flavor].apk`，
`SHA256SUMS.txt` 随 Release 附带。
