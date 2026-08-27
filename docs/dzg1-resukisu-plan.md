# DZG1 使用 ReSukiSU 方案报告

> 报告日期：2026-08-22
> 目标设备：S9280ZCS6DZG1（国行 S9280, One UI 8.5, kernel 6.1.145-android14-11-3254743）
> 方案来源：https://github.com/ReSukiSU/ReSukiSU（基于 SukiSU-Ultra → KernelSU）

---

## 1. ReSukiSU 是什么

[ReSukiSU](https://github.com/ReSukiSU/ReSukiSU) 是 SukiSU-Ultra 的 fork，基于 KernelSU 的 Android 内核级 root 方案。核心特性：

- **完全兼容 KernelSU UAPI**：`KSU_IOCTL_GET_INFO = _IOR('K', 2, struct ksu_get_info_cmd)` 与官方 KernelSU 一致，`KSU_GET_INFO_FLAG_LATE_LOAD = (1U << 2)` 位定义相同
- **支持 LKM 模式**（可加载内核模块）：`Kconfig` 中 `KSU` 为 `tristate`，选择 `m` 编译为 `kernelsu.ko`
- **支持 late-load**：`kernel/core/init.c` 有 `ksu_late_loaded` 机制，检测 `current->pid != 1` 时自动适配 late-load 场景
- **支持 android14-6.1**：`build-all.sh` 中 `KMIS` 列表包含 `android14-6.1`，与 DZG1 内核版本匹配
- **内置 SuSFS 管理**：root 隐藏/防检测工具（backport 到 kernel 4.3+）
- **多管理器支持**：官方 KernelSU / RKSU / MKSU / SukiSU 管理器均可使用
- **双 Hook 模式**：Tracepoint Syscall Redirect（GKI 2.0, 5.10+）和 Manual Hook（3.4–6.18），兼容非 GKI 内核

---

## 2. DZG1 现状

### 2.1 固件与适配

| 项目 | 状态 |
|---|---|
| 固件 | S9280ZCS6DZG1（One UI 8.5），构建号 3254743 |
| 内核 | 6.1.145-android14-11-3254743-abS9280ZCS6DZG1 |
| 与 DZF2 关系 | 构建号相同，函数符号完全一致，数据区有 3.43% 字节差异 |
| exploit 载荷 | 已适配（`target.h` + `p0_fingerprint.h`，参见 `dzg1-target-complete.md`） |
| 真机验证 | 待测（国行 S9280ZCS6DZG1 设备） |

### 2.2 当前 root 方案

```
CVE-2026-43499 exploit → 临时 root (UMH) → su_daemon → SYS_reboot magic
  → ioctl (_IOR('K', 2, ...)) 验证 → exec ksud-s25u-kdp → 加载官方 KernelSU 模块
```

- su_daemon 通过 `SYS_reboot(0xDEADBEEF, 0xCAFEBABE, 0, &fd)` 获取 KernelSU 驱动 fd
- 用 `ioctl(fd, _IOR('K', 2, ...), &info)` 验证 `info.flags & (1U<<0)` (LKM) + `(1U<<2)` (LATE_LOAD)
- 经验证后 exec ksud（KernelSU 守护进程，4.8MB ELF）完成 late-load

---

## 3. 方案可行性分析

### 3.1 ReSukiSU 与 DZG1 的兼容性

| 检查项 | DZG1 要求 | ReSukiSU 支持 | 评估 |
|---|---|---|---|
| 内核版本 | 6.1.145 (GKI 2.0) | 支持 5.10+ GKI 2.0 | ✅ |
| 内核系列 | android14-6.1 | `build-all.sh` 包含 `android14-6.1` | ✅ |
| LKM 加载 | 需编译为 .ko late-load | `KSU` tristate → "m" → `kernelsu.ko` | ✅ |
| late-load 机制 | `ksu_late_loaded` 识别 | `core/init.c` 有完整 late-load 逻辑 | ✅ |
| UAPI 兼容 | `_IOR('K', 2, ...)` + `flags & 4` | `uapi/ksu.h` 定义完全一致 | ✅ |
| 架构 | arm64 | 支持 arm64-v8a | ✅ |

### 3.2 对现有组件的影响

**su_daemon（root helper）**：无需改动。ReSukiSU 的 UAPI 完全兼容现有 `KSU_IOCTL_GET_INFO` 和 `KSU_GET_INFO_FLAG_LATE_LOAD` 检查。

**ksud（守护进程）**：ReSukiSU 仓库自带 `userspace/ksud/` 目录（有 ksud 源码），或可沿用官方 KernelSU 的 ksud 二进制加载 ReSukiSU 的 .ko。需验证 ksud 与 ReSukiSU 内核模块的 supercall 接口兼容性。

**KernelSU Manager（管理器 APK）**：ReSukiSU 支持多管理器，官方 KernelSU 管理器 (`me.weishu.kernelsu`) 可直接使用，无需替换。

### 3.3 编译方式

```bash
# 克隆 ReSukiSU 内核模块
git clone https://github.com/ReSukiSU/ReSukiSU

# 准备 DZG1 对应的 GKI 内核源码（三星开源包）
# 需要 android14-6.1 内核源码树 + DZG1 的 defconfig + vmlinux

# 编译 kernelsu.ko
cd kernel
make KDIR=/path/to/android14-6.1/kernel KSU=m
# 产出：kernelsu.ko

# 验证符号（check_symbol 工具）
make check_symbol KDIR=/path/to/android14-6.1/kernel
```

---

## 4. ReSukiSU 与官方 KernelSU 对比

| 维度 | 官方 KernelSU | ReSukiSU |
|---|---|---|
| **上游** | tiann/KernelSU（mainline） | SukiSU-Ultra → ReSukiSU |
| **LKM 模块** | 支持（kernelsu.ko） | 支持（kernelsu.ko） |
| **late-load** | 支持 | 支持（兼容） |
| **UAPI** | 标准 | 兼容（`_IOR('K', 2, ...)` 一致） |
| **SuSFS** | 需额外编译 | 内置（附带管理工具） |
| **root 隐藏** | 依赖模块 | 开箱即用（SuSFS） |
| **非 GKI 支持** | 有限（5.10+ 为主） | 支持（Manual Hook 3.4–6.18） |
| **管理器兼容** | 仅官方 | 多管理器（KernelSU/RKSU/MKSU/SukiSU） |
| **稳定性** | 成熟（mainline） | 较新 fork，社区较小 |
| **三星适配** | 已验证（S9280 多版本） | 有三星用例（rootmygalaxy 生态） |

---

## 5. 方案步骤

### 5.1 准备阶段

1. **获取 DZG1 GKI 内核源码**：从三星开源中心（opensource.samsung.com）下载 `S9280ZCS6DZG1` 对应的内核源码包，确认 android14-6.1 分支
2. **编译 ReSukiSU kernelsu.ko**：以三星内核源码树为 KDIR，编译 ReSukiSU 内核模块（`KSU=m`）
3. **符号验证**：运行 `check_symbol` 工具验证 .ko 与 DZG1 vmlinux 的符号一致性
4. **ksud 兼容性确认**：测试 ReSukiSU 的 `userspace/ksud` 能否正常加载该 .ko

### 5.2 集成阶段

5. **替换现有模块**：将 App 中的 `ksud-selected`（官方 KernelSU 模块加载器）替换为 ReSukiSU 的对应版本，或保留现有 ksud 仅替换 .ko
6. **编译 DZG1 载荷**：使用已适配的 `target.h` + `p0_fingerprint.h` 编译 `cve-2026-43499-app.stable.so`
7. **构建 APK**：`./gradlew :app:assembleDebug`（RootMyS9280）

### 5.3 验证阶段

8. **真机测试**：在国行 S9280ZCS6DZG1 设备上运行，验证 exploit 临时 root → ReSukiSU late-load → SuSFS root 隐藏全链路
9. **对比测试**：用官方 KernelSU 模块和 ReSukiSU 模块各跑 5 次，对比成功率与稳定性
10. **SuSFS 验证**：测试 root 检测回避（SafetyNet/Play Integrity、三星 KNOX 检测）

---

## 6. 风险与注意事项

### 6.1 已知风险

- **ReSukiSU 较新**：相比官方 KernelSU（tiann 维护），ReSukiSU 是 fork，社区规模较小，文档和 issue 支持有限
- **ksud 兼容性待验证**：官方 ksud 能否正确加载 ReSukiSU 的 .ko 需实测（supercall 接口可能因版本差异报错）
- **三星 GKI 内核 patch**：三星内核可能包含非标准 patch（KDP、RKP、cfi_clang 等），ReSukiSU 的 hook 模式可能受影响
- **后续升级**：如果 ReSukiSU 停止维护，迁移回官方 KernelSU 需要重新编译

### 6.2 收益

- **SuSFS 开箱即用**：无需额外编译内核模块即可获得 root 隐藏能力（对三星 KNOX 检测回避至关重要）
- **多管理器兼容**：用户可选择更熟悉的 KernelSU Manager 或 RKSU/MKSU 管理器
- **Manual Hook 兜底**：如果三星 GKI 的 tracepoint 被禁用（KDP 拦截），可切换到 Manual Hook 模式

### 6.3 注意事项

- DZG1 与 DZF2 构建号相同，符号一致，但 DZG1 载荷已做了专用修正（`nfulnl_logger.name` 偏移 `-0x7e`）。ReSukiSU 模块编译时需使用 DZG1 对应的内核源码（不能用 DZF2 的）
- 编译器版本应与三星内核使用的 clang 版本一致（当前为 clang 17.0.2）
- ReSukiSU 的 `build-all.sh` 中 `android14-6.1` 配置可能默认使用 AOSP 的 GKI 内核，而非三星修改版，需手动调整 KDIR 指向三星内核源码

---

## 7. 结论与建议

### 7.1 可行性评估

**可行，但推荐优先级中等**。ReSukiSU 的 UAPI 兼容性已通过代码审计确认（`_IOR('K', 2, ...)` 与 `KSU_GET_INFO_FLAG_LATE_LOAD` 一致），技术路径无阻塞。主要工作量在编译适配和真机验证。

### 7.2 建议方案

```
短期（DZG1 真机验证通过前）：
  保持现有官方 KernelSU 方案（ksud-s25u-kdp），先完成 DZG1 exploit 载荷验证
    
中期（DZG1 验证通过后）：
  编译 ReSukiSU kernelsu.ko，在 DZG1 上做 A/B 对比测试
  （官方模块 vs ReSukiSU 模块，各 5 次比较成功率）
    
长期（若 ReSukiSU 稳定且有 SuSFS 需求）：
  迁移到 ReSukiSU 方案，发布更新
```

### 7.3 关键依赖

| 依赖 | 状态 | 获取方式 |
|---|---|---|
| DZG1 内核源码 | 三星开源中心（GPL） | opensource.samsung.com |
| ReSukiSU 仓库 | GitHub 公开 | 已 clone 到本机 |
| 三星 clang 编译器 | NDK r29 | 已有 |
| check_symbol 工具 | ReSukiSU 自带 | 已有 |

---

> 参考资源：
> - [ReSukiSU GitHub](https://github.com/ReSukiSU/ReSukiSU)
> - [SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)
> - [KernelSU 官方](https://github.com/tiann/KernelSU)
> - [SuSFS](https://gitlab.com/simonpunk/susfs4ksu)
> - [DZG1 适配完成报告](dzg1-target-complete.md)
> - 本机 ReSukiSU 仓库：`/tmp/resukisu_probe/`