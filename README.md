<p align="center">
  <img src="docs/banner.png" alt="RootMyS24 - 比那名居天子手持 S24 Ultra" width="100%">
</p>

<h1 align="center">RootMyS24</h1>

<p align="center">
  免解锁 root · Samsung Galaxy S24 系列（S9210 / S9260 / S9280）<br>
  基于内核漏洞 <strong>CVE-2026-43499</strong> 的安全研究项目
</p>

<p align="center">
  <a href="https://github.com/NanoTurtle1145/root-my-s24/releases"><img src="https://img.shields.io/badge/version-2.5.5-1E88E5?style=flat-square" alt="Version 2.5.5"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-1E88E5?style=flat-square" alt="GPL-3.0"></a>
  <a href="https://github.com/NanoTurtle1145/root-my-s24"><img src="https://img.shields.io/badge/platform-Android-1E88E5?style=flat-square" alt="Android"></a>
</p>

> 安全研究声明：本项目仅用于安全研究与自有设备维护。使用内核漏洞提权存在导致系统崩溃、数据丢失、设备变砖的风险，使用者需自行承担一切后果。请勿用于非法用途。

---

## 特性

- 免解锁 bootloader：不刷 BL、不升 rev bit，bootloader 保持锁定
- 不熔断 KNOX：e-fuse 状态保持原样（可搭配 KnoxPatch 恢复 Secure Folder 等 KNOX 功能）
- 半持久化：每次重启后运行一次 App 即可重新加载 KernelSU 驱动
- 支持 KernelSU 生态：Zygisk-Next / LSPosed / KnoxPatch 等模块
- 现代 Material 3 界面：动态 ambience 头部、悬浮导航、多语言

## 支持设备

| 机型 | 固件 | 内核 | 状态 |
|---|---|---|---|
| SM-S9280（国行 DZF2） | S9280ZCS6DZF2 | 6.1.145 | 已实测成功（基线） |
| SM-S9280（港版 DZE2） | S9280ZHS6DZE2 | 6.1.145 | 已实测成功 |
| SM-S9280（国行 DZG1） | S9280ZCS6DZG1 | 6.1.145 | 已定标 |
| SM-S9280（港版 CZA1） | S9280ZHS4CZA1 | 6.1.128 | 已定标 |
| SM-S9210（国行 BYH7，One UI 7） | S9210ZCU4BYH7 | 6.1.99 | 已定标，待真机验证 |

> 同平台（e3q/e1q）同构建号固件内核符号一致，可直接通用；跨构建号需逐符号对比修正；跨平台/跨大版本需完整重新定标，且漏洞可能已被修复。适配方法与各目标定标报告见[文档导航](#文档导航)。

## 使用流程

1. 安装 App，授权方式（v2.5.5 起无线调试为实验性开关，默认关闭）：
   - **Shizuku**（默认）：安装并启动 Shizuku（无线/有线 ADB 授权）
   - **无线调试**（实验性）：设置页开启「无线调试授权」后，主页支持通知配对 / 配对码 / 直连
2. 选择目标固件版本，点击「开始 Root」（建议熄屏运行，降低内核竞态概率）
3. 等待 exploit 完成，自动执行 KernelSU late-load
4. 安装 KernelSU Manager（v3.2.5），强制停止后重开，显示「工作中 <LKM> [越狱模式]」

> exploit 是概率性的，失败/重启后重试即可（成功率随尝试累加）。成功标记：`exploit completed` + `retval=0 socket=1`。

---

## 文档导航

完整文档中心见 [docs/README.md](docs/README.md)。

### 入门与使用

- [docs/release-v2.5.5.md](docs/release-v2.5.5.md) —— 最新发布说明（下载 / SHA256 / 系统要求 / 已知问题 / 更新日志）
- [docs/auth-plan.md](docs/auth-plan.md) —— 授权方案规划（Shizuku / 无线调试各方案状态）
- [docs/release-v2.2.md](docs/release-v2.2.md) —— v2.2 发布说明（历史）

### 技术原理

- [docs/technical-principles.md](docs/technical-principles.md) —— 本 App 技术原理（漏洞成因 / 利用链 / 为什么免解锁 / 风险）
- [研究仓库 VULNERABILITY_ANALYSIS.md](https://github.com/NanoTurtle1145/samsung-root-research/blob/main/VULNERABILITY_ANALYSIS.md) —— 完整漏洞原理剖析（研究笔记，权威）

### 固件适配与定标

- [docs/adaptation-guide.md](docs/adaptation-guide.md) —— 适配方法论（移植到新设备/固件先读这篇）
- [docs/dze2-target-complete.md](docs/dze2-target-complete.md) —— 港版 DZE2 定标报告
- [docs/dzg1-target-complete.md](docs/dzg1-target-complete.md) —— 国行 DZG1 定标报告
- [docs/cza1-target-complete.md](docs/cza1-target-complete.md) —— 港版 CZA1 定标报告
- [docs/oneui7-adaptation-report.md](docs/oneui7-adaptation-report.md) —— One UI 7 (BYH7) 可行性报告
- [docs/byh7-target-complete.md](docs/byh7-target-complete.md) —— BYH7 定标完成报告

### 运行日志与故障定位

- [docs/run-log-analysis.md](docs/run-log-analysis.md) —— 成功/失败日志逐行对比与判读
- [docs/sm-s9380-rmg-root-experience.md](docs/sm-s9380-rmg-root-experience.md) —— SM-S9380 实战经验归档

### 研究资料

- [docs/research-index.md](docs/research-index.md) —— 研究资料全量索引
- [docs/blog-s9280-root.md](docs/blog-s9280-root.md) —— 博客：S24 Ultra 国行免解锁 Root 实践
- [samsung-root-research](https://github.com/NanoTurtle1145/samsung-root-research) —— 研究仓库（固件/exploit 工程/定标报告权威镜像）

## 技术原理（概要）

本 App 基于内核漏洞 **CVE-2026-43499**（rtmutex 内核栈 use-after-free）：通过 PI futex 链死锁回滚触发错误清理路径，留下指向已释放内核栈的悬垂指针；复用该栈伪造 `rt_mutex_waiter` 后获得物理内存读写，依次绕过 KASLR、降级 SELinux、以 root 执行辅助程序，最终 late-load KernelSU 驱动。全程不改动持久化分区，bootloader 保持锁定、KNOX e-fuse 不熔断。

> 完整原理（成因 / 利用链 / 防护绕过 / 源码对照）见 [docs/technical-principles.md](docs/technical-principles.md) 与研究仓库 [VULNERABILITY_ANALYSIS.md](https://github.com/NanoTurtle1145/samsung-root-research/blob/main/VULNERABILITY_ANALYSIS.md)。

## 注意事项

- **停在已适配固件，不要升级**：新固件会修复 CVE-2026-43499 相关漏洞
- exploit 概率性成功：失败多试几次，必要时重启手机
- 运行期间建议熄屏（降低内核竞态导致的崩溃概率）
- 每次重启手机后需要重新运行一次「开始 Root」以加载 KernelSU 驱动

## 构建

```sh
./gradlew :app:assembleDebug    # debug APK
./gradlew :app:assembleRelease  # release APK（需自行配置签名）
```

载荷（exploit / root helper / ksud）已内置在 `app/src/main/assets/`。载荷构建链属开发者职责，不在本仓库范围。

## 依赖与致谢

- [CVE-2026-43499](https://github.com/IonStack/CVE-2026-43499) 安全研究（IonStack / NebuSec）
- [Root-My-Galaxy](https://github.com/BuSung-dev/Root-My-Galaxy) 免解锁 root 参考工程
- [KernelSU](https://github.com/tiann/KernelSU)（GPL-2.0）
- [Zygisk-Next](https://github.com/Dr-TSNG/ZygiskNext)
- [LSPosed](https://github.com/LSPosed/LSPosed)（GPL-3.0）
- [KnoxPatch](https://github.com/salvogiangri/KnoxPatch)
- [Vector](https://github.com/JingMatrix/Vector) 界面模板（Material 3 / ambience）

## License

[GNU General Public License v3.0](LICENSE)
