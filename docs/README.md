# RootMyS24 文档中心

> 本目录收纳项目的全部文档。按用途分类，从[根 README](../README.md)「文档导航」章节可直达本页。
> 目标固件定标报告与研究方法论同步镜像于研究仓库 [samsung-root-research](https://github.com/NanoTurtle1145/samsung-root-research)。

---

## 全量文档清单

| # | 文件 | 标题 | 分组 |
|---|---|---|---|
| 1 | [README.md](../README.md) | 项目根 README（简介 / 特性 / 设备 / 导航） | 入门 |
| 2 | [release-v2.5.6.md](release-v2.5.6.md) | v2.5.6 发布说明（地区分组 / 禁用 DZG1 / pipe gate v6） | 发布 |
| 3 | [release-v2.5.5.md](release-v2.5.5.md) | v2.5.5 发布说明（无线调试收敛为实验性开关） | 发布 |
| 4 | [release-v2.2.md](release-v2.2.md) | v2.2 发布说明（列表式固件选择 / BYH7 SELinux 偏移修复） | 发布 |
| 5 | [auth-plan.md](auth-plan.md) | 授权方案规划（Shizuku / 无线调试各方案状态） | 入门 |
| 6 | [technical-principles.md](technical-principles.md) | 本 App 技术原理（漏洞成因 / 利用链 / 风险） | 原理 |
| 7 | [adaptation-guide.md](adaptation-guide.md) | 适配方法论（DZF2 完整复盘） | 定标 |
| 8 | [dze2-target-complete.md](dze2-target-complete.md) | 港版 DZE2 定标报告 | 定标 |
| 9 | [dzg1-target-complete.md](dzg1-target-complete.md) | 国行 DZG1 定标报告（v2.5.6 起已并入 DZF2 载荷） | 定标 |
| 10 | [cza1-target-complete.md](cza1-target-complete.md) | 港版 CZA1 定标报告（One UI 8.0） | 定标 |
| 11 | [oneui7-adaptation-report.md](oneui7-adaptation-report.md) | One UI 7 (BYH7) 可行性报告 | 定标 |
| 12 | [byh7-target-complete.md](byh7-target-complete.md) | BYH7 定标完成报告 | 定标 |
| 13 | [run-log-analysis.md](run-log-analysis.md) | 运行日志分析（DZF2 成功 vs BYH7 失败） | 日志 |
| 14 | [sm-s9380-rmg-root-experience.md](sm-s9380-rmg-root-experience.md) | SM-S9380 实战经验归档（酷安） | 日志 |
| 15 | [research-index.md](research-index.md) | 研究资料全量索引 | 研究 |
| 16 | [blog-s9280-root.md](blog-s9280-root.md) | 博客：S24 Ultra 国行免解锁 Root 实践 | 研究 |

---

## 一、入门与使用

| 文档 | 内容 |
|---|---|
| [根 README](../README.md) | 项目简介、特性、支持设备、使用流程、构建方法、文档导航 |
| [release-v2.5.6.md](release-v2.5.6.md) | **最新发布说明**（v2.5.6）：下载、SHA256、系统要求、已知问题、完整更新日志 |
| [auth-plan.md](auth-plan.md) | 授权方案规划：Shizuku / 无线调试直连 / 配对码 / 通知配对，各方案状态 |

**历史发布说明**：[release-v2.5.5.md](release-v2.5.5.md)（无线调试收敛）、[release-v2.2.md](release-v2.2.md)（列表式固件选择、BYH7 SELinux 偏移修复）

## 二、技术原理

| 文档 | 内容 |
|---|---|
| [technical-principles.md](technical-principles.md) | 本 App 技术原理：漏洞成因、利用链全景、为什么免解锁、风险提示、源码对照 |
| [研究仓库 VULNERABILITY_ANALYSIS.md](https://github.com/NanoTurtle1145/samsung-root-research/blob/main/VULNERABILITY_ANALYSIS.md) | 完整漏洞原理剖析（研究笔记，权威）：rtmutex UAF 成因、防护绕过、worklist 竞态 |

## 三、固件适配与定标（按目标排序）

| 目标 | 平台 / 固件 | 内核 | 文档 |
|---|---|---|---|
| 国行 DZF2（基线，已实测成功） | e3q-S9280ZCS6DZF2 | 6.1.145 | [adaptation-guide.md](adaptation-guide.md) |
| 港版 DZE2（已实测成功） | e3q-S9280ZHS6DZE2 | 6.1.145 | [dze2-target-complete.md](dze2-target-complete.md) |
| 国行 DZG1（v2.5.6 起并入 DZF2 载荷） | e3q-S9280ZCS6DZG1 | 6.1.145 | [dzg1-target-complete.md](dzg1-target-complete.md) |
| 港版 CZA1 | e3q-S9280ZHS4CZA1 | 6.1.128 | [cza1-target-complete.md](cza1-target-complete.md) |
| 国行 BYH7（One UI 7，待真机验证） | e1q-S9210ZCU4BYH7 | 6.1.99 | [oneui7-adaptation-report.md](oneui7-adaptation-report.md) → [byh7-target-complete.md](byh7-target-complete.md) |

**方法论**：[adaptation-guide.md](adaptation-guide.md) —— 从固件提取到真机验证的完整适配流程复盘，移植到新设备/固件时先读这篇。

## 四、运行日志与故障定位

| 文档 | 内容 |
|---|---|
| [run-log-analysis.md](run-log-analysis.md) | S9280 DZF2 成功 vs BYH7 失败逐行对比；失败特征判读表（cache gate / vmemmap 特征 / struct page 偏移） |
| [sm-s9380-rmg-root-experience.md](sm-s9380-rmg-root-experience.md) | SM-S9380 实战经验归档：boot quiet window 时序、struct page 四项偏移、Shell/App 域差异 |

## 五、研究资料索引

| 文档 | 内容 |
|---|---|
| [research-index.md](research-index.md) | 研究资料全量索引（固件/内核/exploit 工程/参考研究/工具链/发布物） |
| [blog-s9280-root.md](blog-s9280-root.md) | 博客文章：S24 Ultra 国行免解锁 Root 实践 |

---

## 六、文档维护约定

1. **发布说明**：每个发布版本一个 `release-vX.Y.Z.md`，最新版在本页全量清单与「入门」分组置顶；旧版保留供回溯。
2. **定标报告**：每适配一个目标写一份 `<目标>-target-complete.md`，标题含平台/固件/内核版本；跨大版本适配先写可行性报告再写定标报告（见 BYH7 两篇）。
3. **日志归档**：原始运行日志放研究仓库 `07_发布物/run_logs/`（`.txt` 不随 App 仓库发布）。
4. **索引同步**：新增/删除/改名任何 docs/ 下的 `.md` 后，必须同步更新本页「全量文档清单」与根 README「文档导航」。
5. **镜像**：研究方法论、定标报告、研究索引与 [samsung-root-research](https://github.com/NanoTurtle1145/samsung-root-research) 仓库同步维护，两仓库版本以研究仓库为权威。
