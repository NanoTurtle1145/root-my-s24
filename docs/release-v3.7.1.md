# RootMyS24 v3.7.1

修复 3.7.0 的**载荷打包问题**：机型条目清单（`app/series_entries.json`）自 10-26 后
没有随新条目更新，导致 3.7.0 的官方系列包里缺少 9 个条目，DZJ3 / S24 FE 甚至完全看不到。

## 修复

### 条目清单重新生成

从 `FirmwareVersion` 枚举重新生成 `app/series_entries.json`（沿用同一规则：
已实测 → 对应系列包 + all/exp；未实测 → 只进 all/exp）：

| 包 | 3.7.0 | 3.7.1 | 补回 |
|---|---|---|---|
| s24 | 4 | **10** | S921EDZIF、S9280EZJ1、S928BEZJ1、S928BEZJB、S9260TW_DZE2、BYH7、CZA1 等 |
| fold | 1 | **5** | F9560DZH5、F956NEZIH、F966BBZG3 等 |
| w | 0 | **2** | W9025DZH5、W9025DZG3 |
| all / exp | 62 | **70** | 全部条目（含 **S9280DZJ3**、**S721BEZIF** 等未实测载荷） |

### 载荷资产补齐

按条目引用把缺失的 payload 复制进对应 flavor 资产目录：

- s24：`cve-2026-43499-byh7`、`cve-2026-43499-cza1`、`cve-2026-43499-dze2`
- fold：`cve-2026-43499-F966BXXSBBZG3`、`cve-2026-43499-S928BXXU6EZIF`
- w：`cve-2026-43499-W9025ZCS4DZG3`

`S9260CZC1` 是 gamma 一对一渠道专用条目，保持只存在于 gamma 包（不进 all/exp）。

## 关于 DZJ3 载荷

DZJ3（SM-S9280 国行 DZJ3）的 98260 载荷一直在仓库里：

- 目标载荷：`app/src/main/assets/samtimer/targets/S9280ZCS6DZJ3/libtimer`
- 定标说明：`app/src/main/cpp/targets/S9280ZCS6DZJ3/{offsets.h,BUILD.md}`

它标记为 `tested = false`（社区手动 staging 成功，App 内未验证），按打包规则只进
**all / exp** 包，不进官方系列包。3.7.0 的问题是清单没更新、连 all/exp 都没列出来；
3.7.1 起在 all/exp 里可以正常选到，开了「未测试载荷」后即可使用。
