# DirtyFrag 引擎接入方案（alpha 线）

> 状态：方案定稿，待实现。目标渠道 `alphaN`（引擎试验），与 `betaN`（机型适配）区分。
> 关联参考：`~/项目/samsung_root_research/03_参考研究/dirtyfrag/`（**只读参考**）

## 0x00 为什么再加一个引擎

现有 GhostLock 链（CVE-2026-43499）是**竞态型**：`triggered=0` 是常态，需要 Shizuku/ADB 配对把
payload 以 shell 域跑起来，root 仅 per-boot，且跑完机器处于脆弱态（解锁可能卡死）。

DirtyFrag（CVE-2026-43284，xfrm-ESP 页缓存写）是**确定性**逻辑漏洞，且：

| 维度 | GhostLock（现有） | DirtyFrag（新增） |
| --- | --- | --- |
| 触发 | 竞态，概率 | 确定性，失败不 panic |
| 前置权限 | 需要 Shizuku/ADB 配对 | **app 级 API（IpSecManager），不需要任何特殊权限** |
| 持久性 | per-boot，需手动重跑 | 可**开机自动恢复**（LOCKED_BOOT_COMPLETED） |
| 副作用 | 短期内核脆弱态 | **页缓存污染**（须 drop_caches / 重启）+ 加载 permissive LKM |

两者适用条件**互相独立**（各自的内核补丁状态不同），所以不是替代而是**互补**：先试 DirtyFrag，
不可用再退回 GhostLock。

## 0x01 许可证红线（重要）

参考仓库及其上游（`2253845067/DFRoot`、`V4bel/dirtyfrag`、`lsposed/lspromise`、
`polygraphene/DFReroot`、`combeng6th/DirtyInit`）**全部没有 LICENSE**：

- ❌ 不把它们的任何代码/产物放进本仓库
- ❌ 不把它们的 APK/ko 作为发布物分发
- ✅ 只读参考其**设计思路与接口形态**，实现全部自己写
- ✅ 一次性本地可行性验证可以借用其产物，但不入库、不发布

## 0x02 架构：引擎抽象

```
rootmy/
  RootViewModel.kt            ← 现有流程编排（保持：Shizuku、固件选择、ksud、遥测都在这层）
  engine/
    RootEngine.kt             ← 接口：probe(): EngineAvailability / run(callbacks): EngineResult
    GhostLockEngine.kt        ← 现有 payload 路径，包成 Engine（行为不变）
    DirtyFragEngine.kt        ← 新引擎（alpha，默认关闭）
  dirtyfrag/
    DfDeviceCheck.kt          ← 启动自检：机型/KMI/6 条路径/SELinux 标签/钩子符号
    DfIpsec.kt                ← IpSecManager 建 UdpEncapsulationSocket + ESP transform
    DfPatch.kt                ← 页缓存块改写（IV 计算、splice、目标 lib 定位）
    DfNative.kt               ← JNI 入口
  boot/
    DfBootReceiver.kt         ← LOCKED_BOOT_COMPLETED / BOOT_COMPLETED 自动恢复
```

编排策略（`RootViewModel`）：

1. `DirtyFragEngine.probe()` → 自检全过 **且** 开关已开 → 走它
2. 否则 / 失败 → 回落 `GhostLockEngine`（现有逻辑一行不改）
3. UI 显示当前用的是哪个引擎、自检报告、失败原因

## 0x03 需要的资产（`app/src/main/assets/`）

| 资产 | 来源 | 说明 |
| --- | --- | --- |
| `dirtyfrag-android14-6.1.ko` | **自己用 DDK 编**（`ghcr.io/ylarod/ddk-min:android14-6.1`），对 `vmlinux_dzh3.elf` 做符号审计 | 写进 `/vendor/lib64/libstagefrighthw.so` 后由 vendor_modprobe 加载；permissive 用途 |
| 钩子 shellcode（libc / libc++ 两份） | **自己写**（汇编，按参考的接口形态：patch 到 `__libc_init` / ostream sentry） | 运行时从 ELF 解析符号偏移，不硬编码 |
| `splicehelper` | 自己写 | crash_dump64 跳板，用 CBC 原语改写后读 vendor 库页 |
| ksud | 用我们自己的 3.3.0(32601) | **需补 3 个参数**：`--stage-from` / `--soft-reboot` / `--ro-partitions`（参考实现固定传这三个，否则参数解析失败） |

## 0x04 设备门槛自检（照参考的 DeviceCheck 思路自己实现）

启动/运行前必须全过，任一不过就**拒绝动手**（避免 patch 到一半失败）：

```
机型/KMI     : SM-S9280 / 6.1.145-android14-11-3254743-abS9280ZCS6DZH3 → android14-6.1
必需路径     : /vendor/lib64/libstagefrighthw.so   (vendor_file 标签)
               /apex/com.android.runtime/bin/crash_dump64 (crash_dump_exec 标签)
               /system/lib64/libc.so → apex .../bionic/libc.so   (真身在 APEX)
               /system/lib64/libc++.so   (真文件，非链接)
               /vendor/bin/modprobe → toolbox (applet)
               /system/bin/logcat
钩子符号     : libc: __libc_init ✔   libc++: _ZNSt3__113basic_ostreamIcNS_11char_traitsIcEEE6sentryC1ERS3_ ✔
SELinux      : 期望 Enforcing（干净基线）
```

> 2026-09-13 已在 SM-S9280/DZH3 上实测：以上全部满足 ✔

## 0x05 设置项（默认关闭）

- **DirtyFrag 引擎（alpha）** —— 开关，默认关
- **开机自动恢复 root** —— 开关（仅 DirtyFrag 引擎支持）
- **运行后自动 drop_caches** —— 默认开（页缓存污染清理）
- 自检报告入口（关于页/设置页），展示 0x04 的逐项结果

## 0x06 风险与安全约束

1. **页缓存污染**：跑完必须 `echo 3 > /proc/sys/vm/drop_caches` 或重启，否则系统行为可能异常
2. **permissive LKM**：会把 SELinux 置为 Permissive，重启清除；UI 必须明确告知
3. **只读分区保护**：不写 `/system`（EROFS），走 bind-mount 方式（参考实现的做法）
4. **默认不给 adb shell 提权**：不传 `--allow-shell`（与参考实现一致，安全性优先）

## 0x07 里程碑（alpha 线）

| 版本 | 内容 | 验收 |
| --- | --- | --- |
| `alpha1` | 设备自检页 + 引擎开关（不做实际注入） | App 能正确报告 0x04 逐项结果 |
| `alpha2` | DirtyFrag 引擎接入（手动触发） | 手动点一次走完全链 → KernelSU 起来 |
| `alpha3` | 开机自动恢复 + 失败回落 GhostLock | 重启后自动恢复；DirtyFrag 不可用时自动走 GhostLock |

## 0x08 待定

1. 内核补丁状态最终判定：`esp_input` 缺 `SKBFL_SHARED_FRAG` 位测试（指向未修），
   `ip_output`/`ip6_output` 侧因 `+lto` 符号内部化无法静态判定 → **以真机实测为准**
2. ksud 三个新参数是自己实现，还是改走参考的 ksud（后者有许可证问题 → 倾向自己实现）
3. 是否把 GhostLock 也保留为独立可选项（预计保留，作为回退）
