# RootMyS24 文档中心

> 本目录收纳项目全部文档，按用途分组。根 [README](../README.md) 是入口，本页是完整索引。
> 目标固件定标报告与研究方法论同步镜像于研究仓库 [samsung-root-research](https://github.com/NanoTurtle1145/samsung-root-research)（以研究仓库为权威）。

---

## 分组索引

### 一、入门与使用

| 文档 | 内容 |
|---|---|
| [根 README](../README.md) | 项目简介、快速开始、支持设备、项目结构、构建方法 |
| [贡献指南](../CONTRIBUTING.md) | 报告问题要带什么、两种引擎的适配流程、PR 硬性要求、许可注意 |
| [授权方案](auth-plan.md) | Shizuku / 无线调试直连 / 配对码 / 通知配对，各方案状态 |
| [隐私政策](PRIVACY.md) | 运行日志怎么上报、设备标识与 IP 如何处理 |
| [BakaSU 内核模式](bakasu-kernel-mode.md) | 实验开关：用三星适配的 BakaSU LKM 替代 KernelSU |

### 二、发布说明

| 版本 | 时间 | 要点 |
|---|---|---|
| [v3.7.0-alpha.1](release-v3.7.0-alpha.1.md) | 2026-11-20 | **alpha**：KernelSU 3.3.1 KDP 驱动（含 One UI 9）、机型页自动检测/按内核构建号覆盖、设置两页、日志页历史独立页面 + 编号开跑即生成 |
| [v3.6.4-beta.1](release-v3.6.4-beta.1.md) | 2026-11-14 | 预发布：per-target 载荷（首个 DZJ3）、#35 禁用模块改 `disable` 标记 |
| [v3.6.3](release-v3.6.3.md) | 2026-11-10 | 运行日志本机存档 + 日志编号、本机历史日志 |
| [v3.6.2](release-v3.6.2.md) | 2026-11-08 | 安全拆除（软重启不再硬重启）、一次开机一次尝试强制 |
| [v3.6.0](release-v3.6.0.md) | 2026-11-07 | 新增 98260 UAF 引擎（双引擎切换） |
| [v3.5.6](release-v3.5.6.md) | 2026-11-07 | UI 修正（隐私卡 / Markdown / 安装 ID） |
| [v3.5.5](release-v3.5.5.md) | 2026-11 | EZIF 支持与 KSU 版本选择 |
| [v3.5.2](release-v3.5.2.md) | 2026-10 | 欧版 S24 Ultra 全线支持、EZIF 实测通过、三代新载荷待验证 |
| [v3.5.1](release-v3.5.1.md) | 2026-10 | 韩版 Z Flip6 支持、BakaSU 实验模式 |
| [v3.5.0](release-v3.5.0.md) | 2026-10 | KernelSU 3.3.0 适配完成 |
| [v3.4.0](release-v3.4.0.md) | 2026-09 | 历史版本 |
| [v2.5.6](release-v2.5.6.md) / [v2.5.5](release-v2.5.5.md) / [v2.2](release-v2.2.md) | 2026-09 | 地区分组、无线调试收敛、列表式固件选择 |

### 三、设计规范

| 文档 | 内容 |
|---|---|
| [UI-GUIDELINES.md](UI-GUIDELINES.md) | **改界面之前先读**：圆角尺度（8/12/20/28/32）、行卡片 index/count 规则、间距刻度、标准组件、7 条常见错误、改动检查清单 |

### 四、技术原理

| 文档 | 内容 |
|---|---|
| [technical-principles.md](technical-principles.md) | 本 App 技术原理：漏洞成因、利用链全景、为什么免解锁、风险提示、源码对照 |
| [研究仓库 VULNERABILITY_ANALYSIS.md](https://github.com/NanoTurtle1145/samsung-root-research/blob/main/VULNERABILITY_ANALYSIS.md) | 完整漏洞原理剖析（权威）：rtmutex UAF 成因、防护绕过、worklist 竞态 |

### 五、固件适配与定标

**方法论**

| 文档 | 内容 |
|---|---|
| [adaptation-guide-full.md](adaptation-guide-full.md) | 完整适配指南（手把手，照着做即可；含判据与失败分类）⭐ |
| [adaptation-guide.md](adaptation-guide.md) | 适配方法论（旧版，DZF2 完整复盘，偏原理） |

**定标报告（按引擎分）**

| 引擎 | 目标 | 平台 / 固件 | 内核 | 文档 |
|---|---|---|---|---|
| 98260 | SM-S9280 国行 DZJ3 | e3q-S9280ZCS6DZJ3 | 6.1.145 | [port-98260-S9280ZCS6DZJ3.md](port-98260-S9280ZCS6DZJ3.md) |
| 98260 | SM-S721B 欧版 S24 FE（**待验证**） | r12s-S721BXXUEEZIF | 6.1.162 | [port-98260-S721BXXUEEZIF.md](port-98260-S721BXXUEEZIF.md) |
| 98260 | SM-S9280/928B 港版·欧版 EZIF | e3q-S9280ZHU6EZIF / S928BXXU6EZIF | 6.1.162 | [s928b-ezif-target-complete.md](s928b-ezif-target-complete.md) |
| 43499 | 国行 DZF2（基线，已实测） | e3q-S9280ZCS6DZF2 | 6.1.145 | [adaptation-guide.md](adaptation-guide.md) |
| 43499 | 港版 DZE2（已实测） | e3q-S9280ZHS6DZE2 | 6.1.145 | [dze2-target-complete.md](dze2-target-complete.md) |
| 43499 | 美版 S926U1 DZE2（待验证） | e2q-S926USQS6DZE2 | 6.1.145 | [dze2-xaa-target-complete.md](dze2-xaa-target-complete.md) |
| 43499 | 韩版 F741N DZE1（已实测） | b6q-F741NKSS4DZE1 | 6.1.145 | [f741n-dze1-target-complete.md](f741n-dze1-target-complete.md) |
| 43499 | 国行 DZG1（并入 DZF2） | e3q-S9280ZCS6DZG1 | 6.1.145 | [dzg1-target-complete.md](dzg1-target-complete.md) |
| 43499 | 港版 CZA1 | e3q-S9280ZHS4CZA1 | 6.1.128 | [cza1-target-complete.md](cza1-target-complete.md) |
| 43499 | S9210 国行 DZI1 | e1q-S9210ZCS6DZI1 | 6.1.145 | [port-e1q-S9210ZCS6DZI1.md](port-e1q-S9210ZCS6DZI1.md) |
| 43499 | 国行 BYH7（One UI 7，待验证） | e1q-S9210ZCU4BYH7 | 6.1.99 | [oneui7-adaptation-report.md](oneui7-adaptation-report.md) → [byh7-target-complete.md](byh7-target-complete.md) |
| 43499 | S928B 欧版 AXK5/BYD9/CZA1 | e3q-S928BXXU4* | 6.1.75–6.1.128 | [s928b-eur-axk5-byd9-cza1-target-complete.md](s928b-eur-axk5-byd9-cza1-target-complete.md) |

### 六、运行日志与故障定位

| 文档 | 内容 |
|---|---|
| [run-log-analysis.md](run-log-analysis.md) | DZF2 成功 vs BYH7 失败逐行对比；失败特征判读表（cache gate / vmemmap / struct page 偏移） |
| [run-log-success-analysis.md](run-log-success-analysis.md) | 云日志成功率整理：按构建号+引擎统计独立 install，定义「转已测试」的判定规则 |
| [sm-s9380-rmg-root-experience.md](sm-s9380-rmg-root-experience.md) | SM-S9380 实战经验：boot quiet window 时序、struct page 四项偏移、Shell/App 域差异 |

> 反馈问题时请附 **运行日志编号**（App → 运行日志 →「复制编号」，v3.6.3 起）。

### 七、构建与发布

| 文档 | 内容 |
|---|---|
| [ci-release.md](ci-release.md) | GitHub Actions 签名构建与 Release 流程、所需 Secrets |
| [separate-builds.md](separate-builds.md) | 分系列 APK（all / s24 / fold / exp）构建与渠道语义 |

### 八、实验与规划

| 文档 | 内容 |
|---|---|
| [bakasu-kernel-mode.md](bakasu-kernel-mode.md) | BakaSU 实验内核模式（三星 KDP/RKP/DEFEX） |
| [dirtyfrag-engine-plan.md](dirtyfrag-engine-plan.md) | DirtyFrag 引擎规划（alpha，未采用） |
| [dzg1-resukisu-plan.md](dzg1-resukisu-plan.md) | DZG1 ReSukiSU / BakaSU 路线规划 |

### 九、社区方案评估

| 文档 | 内容 |
|---|---|
| [community-kdp-ksu-331.md](community-kdp-ksu-331.md) | DZJ3 的 KernelSU 3.3.1 KDP 实验版：解决的问题、产物 SHA-256 核对、构建方式、限制、并入本项目的三条路 |

### 九、研究资料

| 文档 | 内容 |
|---|---|
| [research-index.md](research-index.md) | 研究资料全量索引（固件/内核/exploit 工程/参考研究/工具链/发布物） |
| [blog-s9280-root.md](blog-s9280-root.md) | 博客：S24 Ultra 国行免解锁 Root 实践 |
| [forum-and-admin.md](forum-and-admin.md) | 论坛与管理端说明 |

---

## 文档维护约定

1. **发布说明**：每个发布版本一个 `release-vX.Y.Z.md`；预发布写在同一文件（如 `release-v3.6.4-beta.1.md`）。
2. **定标报告**：43499 用 `<目标>-target-complete.md`；98260 用 `port-98260-<TAG>.md`，与 `app/src/main/assets/samtimer/targets/<TAG>/` 对应。
3. **日志归档**：原始运行日志放研究仓库 `07_发布物/run_logs/` 与 `devices_ports/`（不随 App 仓库发布）。
4. **索引同步**：新增/删除/改名 `docs/` 下的 `.md` 后，同步更新本页与根 README「九、文档导航」。
5. **镜像**：研究方法论、定标报告与 [samsung-root-research](https://github.com/NanoTurtle1145/samsung-root-research) 同步，冲突时以研究仓库为权威。
