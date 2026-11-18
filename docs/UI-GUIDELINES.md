# UI 设计规范（改界面之前先读这篇）

> 这份文档存在的唯一目的：**别每次改 UI 都重新猜圆角和尺寸**。
> 所有数值都来自当前代码（`ui/theme/Theme.kt` + `ui/components/*` + `ui/screens/*`），
> 改 UI 时照抄下表，不要临时拍一个 `16.dp`。

## 0. 三条铁律

1. **圆角只用一套尺度**：`8 / 12 / 20 / 28 / 32`（见下表），出现其它值必须在 PR 里说明理由。
2. **`GroupedRow` 一行只能放一个 `ListItem`**——它是 `Surface { content() }`，Surface 内部是 `Box`，
   放两个子项会**重叠**（2026-11 实际踩过：设置页两行文字叠在一起）。
3. **间距只用 4 的倍数**（2/4/8/12/16/20/24）；6/10/14 只允许出现在紧凑的行内布局（如卡片内文字块）。

## 1. 圆角尺度

主题里已经定义好 `MaterialTheme.shapes`（`ui/theme/Theme.kt`）。**代码里优先写 token，不写裸 dp**：

- 卡片 / 气泡 → `MaterialTheme.shapes.medium`
- 小提示块 / chip → `MaterialTheme.shapes.small`
- 搜索框 → `MaterialTheme.shapes.large`
- 底部 panel → `MaterialTheme.shapes.extraLarge`

仍允许写裸 dp 的**只有四类例外**（都在下表和 §1.1/§1.2 里说明）：
① 行卡片"关节"圆角公式（20→4）；② Markdown 代码块 4/6；③ 72dp 图标底座 16；④ 徽标 8/10。

| Token | 值 | 用在哪 |

| Token | 值 | 用在哪 |
|---|---|---|
| `shapes.extraSmall` | **8 dp** | 小标签、代码块、Markdown 行内元素 |
| `shapes.small` | **12 dp** | 提示条 / 说明块（如机型页的兼容范围提示） |
| `shapes.medium` | **20 dp** | **卡片默认**（公告卡、Snackbar、列表容器、设置卡片） |
| `shapes.large` | **28 dp** | 大容器：搜索框（药丸）、底部面板 |
| `shapes.extraLarge` | **32 dp** | 全屏/半屏 sheet 的顶部圆角 |

### 1.1 行组成卡片的"关节圆角"

列表卡片不是一整块，而是**每行一个 `Surface`**，靠 `index/count` 拼出大圆角：

| 位置 | 上圆角 | 下圆角 |
|---|---|---|
| 第一行（`index == 0`） | 20 dp | 4 dp |
| 中间行 | 4 dp | 4 dp |
| 最后一行（`index == count - 1`） | 4 dp | 20 dp |
| 单行卡片（`count == 1`） | 20 dp | 20 dp |

```kotlin
private fun GroupedRow(index: Int, count: Int, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(
        topStart = if (index == 0) 20.dp else 4.dp,
        topEnd   = if (index == 0) 20.dp else 4.dp,
        bottomStart = if (index == count - 1) 20.dp else 4.dp,
        bottomEnd   = if (index == count - 1) 20.dp else 4.dp,
    )
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer) { content() }
}
```

**行的间距由调用方给**：`Arrangement.spacedBy(4.dp)`（全局 17 处用它，别自己写 6/8）。

### 1.2 不归 MaterialTheme 管的圆角

| 元素 | 圆角 | 说明 |
|---|---|---|
| 应用图标 / 头像底座 | 16 dp（72 dp 图标） | `AboutScreen`、`DonateScreen` 的 `.clip(...)` |
| 小徽标 / 状态 pill | 10～12 dp | 机型页状态标签 |
| 代码块 / 行内 code | 4～6 dp（Markdown） | `ui/components/Markdown.kt` |
| Toast / Snackbar 气泡 | 20 dp | `VectorSnackbar` |
| 状态徽标 / chip | 8～12 dp | `TestStateChip` 用 8 dp（`shapes.extraSmall`），标签类 10～12 dp |

## 2. 尺寸与间距

### 2.1 页面骨架

| 项 | 值 | 备注 |
|---|---|---|
| 页面水平内边距 | **16 dp** | 25 处 `padding(horizontal = 16.dp)`，是默认值 |
| 区块之间（Section） | 16 dp（`SectionLabel` 自带 `top = 16.dp`） | |
| 卡片之间 | 4～8 dp | 同一张卡片内的行 4 dp；不同卡片 8 dp |
| 卡片内部 | 16 dp（水平）；上下 10～12 dp | `ListItem` 自带则为默认 |
| 列表底部留白 | 24 dp | `Spacer(Modifier.height(24.dp))` |

### 2.2 组件尺寸

| 组件 | 尺寸 |
|---|---|
| 触控目标 | **≥ 48 dp**（`IconButton` / `Switch` / 行高都满足，别把行压到 40 以下） |
| `ListItem` 单行 | 默认 56 dp；带 `supportingContent` ≈ 72 dp |
| 图标（行内） | **20 dp**（`AnnouncerCard`）或 **24 dp**（`ListItem.leadingContent`） |
| 图标（空状态/头图） | 72 dp |
| 搜索框 | 高 56 dp，圆角 **28 dp**（药丸） |
| `FilterChip` | Material 默认（高 32 dp），横向 `spacedBy(8.dp)` |
| 分段按钮 | 用 `FilterChip` 拼（本仓库 Material3 版本的 `SegmentedButton` 会因实验 API 解析失败） |
| 开关 `Switch` | Material 默认，不自定义尺寸 |

### 2.3 文字

| 用途 | 样式 |
|---|---|
| 页面标题（Banner 主标题） | `headlineMedium` |
| 页面副标题 | `bodyMedium` + `onSurfaceVariant` |
| 区块标签（`SectionLabel`） | `titleSmall` + **`colorScheme.primary`**，`padding(top = 16.dp, bottom = 4.dp)` |
| 卡片标题 | `bodyLarge`（`ListItem` 默认） |
| 卡片说明 | `bodyMedium` + `onSurfaceVariant` |
| 辅助/状态文字 | `labelSmall` 或 `bodySmall` + `onSurfaceVariant` |
| 等宽（版本号/哈希） | `VectorMono`（`ui/theme`） |

## 3. 颜色

- **只用 `MaterialTheme.colorScheme.*`**，不写死 `Color(0xFF...)`（主题支持动态取色 / AMOLED / 自定义种子色）。
- 卡片背景：`surfaceContainer`；卡片内行（`GroupedRow` 内）用 `cardRowColors = ListItemDefaults.colors(containerColor = Color.Transparent)`，避免叠两层色块。
- 选中态：`secondaryContainer`（面板）/ `primaryContainer`（强调卡片）。
- 错误/警告：`errorContainer` + `onErrorContainer`。
- 禁用/次要：`onSurfaceVariant`。

## 4. 标准组件（直接用，别重写）

| 组件 | 文件 | 说明 |
|---|---|---|
| `BannerHeader` | `ui/components/BannerHeader.kt` | 页面顶部标题：`headlineMedium` + `bodyMedium`，`vertical = 20.dp, horizontal = 4.dp` |
| `AnnouncerCard` | `ui/components/AnnouncerCard.kt` | 公告卡：20 dp 圆角、`spacedBy(8.dp)`、图标 20 dp |
| `VectorAlertDialog` | `ui/components/VectorAlertDialog.kt` | 统一对话框 |
| `VectorSnackbar` | `ui/components/VectorSnackbar.kt` | 20 dp 圆角气泡 |
| `SectionLabel` | 各 Screen 内私有 | 见 §2.3；新页面复制这个实现，不要另起样式 |
| `GroupedRow` | 各 Screen 内私有 | 见 §1.1；**一个组一行** |

## 5. 常见错误（都在这仓库里真实发生过）

1. ❌ 一个 `GroupedRow` 里塞两个 `ListItem` → **两行重叠**。拆成两个 `GroupedRow`。
2. ❌ 用 `RoundedCornerShape(16.dp)` 当卡片 → 卡片是 **20 dp**，16 只用于图标底座。
3. ❌ 行间 `spacedBy(6.dp)` / `spacedBy(10.dp)` → 统一 **4 dp**。
4. ❌ 页面内边距写 12/14/24 → 默认 **16 dp**，只有特殊窄布局才用 12。
5. ❌ 在 `Box` 里叠两个内容不为对齐、而是当"顺序布局"用 → 需要顺序一律 `Column`。
6. ❌ 写死颜色 → 用 `colorScheme`（否则暗色/AMOLED 下会瞎）。
7. ❌ 用 `SegmentedButton`（本仓库 Material3 版本解析不到）→ 用 `FilterChip`。

## 6. 改 UI 前的检查清单

- [ ] 圆角来自 §1 的表，没有新数值？
- [ ] `GroupedRow` 每个只放一行、`index/count` 与所在卡片一致？
- [ ] 间距是 4 的倍数、页面水平 16 dp？
- [ ] 颜色全部走 `MaterialTheme.colorScheme`？
- [ ] 新增组件有没有复用 `BannerHeader` / `SectionLabel` / `GroupedRow`？
- [ ] 真机看一眼：暗色模式 + AMOLED + 大字体（`fontScale 1.3`）下有没有截断/重叠？

## 7. 相关文件

```
app/src/main/kotlin/cn/nanoturtle/rootmys9280/manager/ui/
├── theme/Theme.kt            # Shapes / 颜色方案 / 动态取色 / AMOLED
├── theme/Type.kt             # 字体与 VectorMono
├── components/               # BannerHeader / AnnouncerCard / VectorSnackbar / Markdown ...
├── screens/home|rootflow|settings|about|logs|wiki|donate|feedback|onboarding/
└── firmware/FirmwareSelectActivity.kt
```
