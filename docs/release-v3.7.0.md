# RootMyS24 v3.7.0

> **正式版**。相对 v3.6.4：新增 KernelSU 3.3.1 KDP 驱动、S24 FE（r12s）定标、
> 内核构建安全闸门，机型页/设置页/日志页重构，界面动效整理。

## 新增

### KernelSU 3.3.1 KDP 驱动
- 驱动版本新增 **3.3.1 KDP**：官方 KernelSU v3.3.1 + 三星 KDP/RKP/DEFEX 兼容补丁，本仓库 CI 自编
- LKM 按 android14-6.1 编译，`ksuinit` 用户态加载并在运行时替换 vermagic，
  DZJ3（6.1.145-3254743）与 One UI 9（6.1.162-34343818）共用同一份 ksud
- 配套管理器：官方 KernelSU v3.3.1（32680）
- 实测：SM-S9280 / S9280ZHS6EZJ1 加载成功，`ksud 3.3.1 (uapi: 5)`，`su` 正常

### S24 FE（SM-S721B / r12s）98260 定标（实验）
- 从 `S721BXXUEEZIF` 的 `boot.img` 提取 kallsyms + BTF，逐字段对比 EZIF 结构体布局（一致）
- 重新定标符号地址与三个 `kmem_cache_alloc` 调用点；新增 target 资产
  `samtimer/targets/S721BXXUEEZIF/libtimer`
- 物理基址改为**运行时扫描**（`RMS_PHYS_SCAN`）：r12s 上 `KERNEL_PHYS_LOAD + slide` 不成立，
  实测 UAF/plant/marker 通过、UMH 读回垃圾 → 扫描 `wq→dfl_pwq→pool→worklist` 确定真实基址
- 机型页新增 **SM-S721B 欧版 S24 FE** 条目（未验证，需在设置里开启「未测试载荷」）

### 内核构建安全闸门（EZK1 事故的修复）
- 发现 `uname -r` 的构建号**不能唯一标识内核**：EZIF（Sep 19）/EZJ1（Oct 21）/EZK1（Nov 9）
  都是 `34343818`，但 EZK1 是重编内核，98260 打上去 4 秒重启
- 98260 条目新增拦截：
  - 构建码黑名单（`EZK1`）
  - 内核构建日期黑名单（`2026-11-09`，从 `/proc/version` 经 shell 解析）
- 机型页显示内核构建日期（如 `Kernel build 2026-10-21`），命中黑名单时红色提示、不再推荐

### 环境隐藏
- 设置 → 高级与诊断 → **隐藏 su 兼容模式**：跑完 root 后关闭 `su_compat`
- 清理旧版本遗留的 `/system/bin/logcat` 泄漏挂载

### 机型页
- 顶部「当前设备（自动检测）」卡：型号 · 构建号 · 内核串 · 推荐引擎/驱动/实测状态
- 98260 覆盖按**内核构建号**判定（EZIF/EZJ1/EZJB/EZJD 等 userspace 更新自动命中）
- 同载荷条目合并为 target 行，需要时展开；选中后不再自动返回首页
- 设置页拆「常用 / 高级与诊断」两页；关于页 FAQ 默认折叠

### 运行日志
- **历史日志**改为工具栏同级按钮，进入独立页面（列表 + 全文对话框）
- **日志编号开跑即生成**，跑一半也能复制；编号改为取 RUN-ID 尾部毫秒 8 位（每轮唯一）
- KSU 已加载且驱动为 3.3.x 时，底部浮出**软重启（加载模块）**按钮；
  修复「App 重启后 UserService 未连接」导致按钮失效的问题（改为按需重新绑定）

### 工程
- nightly 渠道 + `.github/workflows/nightly.yml`（每日自动构建、滚动 pre-release）
- `docs/UI-GUIDELINES.md`：圆角/尺寸/间距硬性规范
- KDP 驱动构建流水线（`.github/workflows/ksu-kdp.yml`，DDK 镜像 + 可复现构建）
- 界面动效整理：Material 3 Expressive motion spec、呼吸动画按状态收窄、
  手写帧循环遵循系统动画倍率；日志页使用 Wavy/Loading 指示器
- 论坛迁移到 Discuz! X3.5（服务端，App 入口不变，自动 SSO 登录）

## 已知限制

- 3.3.1 KDP 为实验驱动：加载失败最坏会 panic/重启；模块生效依赖一次 zygote 重启
- S24 FE 载荷尚未真机验证成功（alpha 渠道在跟）
- **EZK1（2026-11-09 内核构建）不支持 98260**：该内核被重编，实测 4 秒重启；App 会拦截
- 98260 一次开机只能跑一次；失败请真重启
- 管理器按钮的软重启走官方 stop/start（框架重启，内核不重启）；App 内按钮走补丁版路径
- 3.3.0 驱动在 6.1.162 上仍不推荐（stock 模块偶发 system_server 崩溃循环）

## 校验

产物命名 `RootMyS24-v3.7.0-<code>-build<code>[-flavor].apk`，`SHA256SUMS.txt` 随 Release 附带。
