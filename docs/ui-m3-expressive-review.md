# UI 评审：以 Material 3 Expressive 为标尺（侧重取色与动效）

> **评审对象**：RootMyS9280（发布名 RootMyS24）`main` @ `e50fab7`（定稿时 HEAD 已到 `59bb9f7`，该提交只动 98260 载荷与 `docs/port-98260-*`，未触及 UI 代码，行号不受影响）
> **依赖事实**：`material3 1.5.0-alpha25`（显式压在 BOM 之上）、`compose-bom 2026.06.01`（实测 Compose 1.12.0-beta01）、`navigation3-ui 1.1.5`、Kotlin 2.4.10、AGP 9.3.1
> **方法**：源码逐文件核对 → 依赖 AAR 反查 API 与 token 数值 → 自建 LCh 生成器数值复算 → 官方规范/源码对照。
> **性质**：只读评审，未改动任何代码；文中路径缩写 `ui/` = `manager-ui/src/main/kotlin/cn/nanoturtle/rootmys9280/ui/`，`app/` = `app/src/main/kotlin/cn/nanoturtle/rootmys9280/manager/`。

---

## 一、结论摘要

**这个项目的 M3 地基比多数同类 App 都好**：主题用的是 `MaterialExpressiveTheme` + `MotionScheme.expressive()`（`app/ui/theme/Theme.kt:59-66`），配色覆盖 36/48 个角色且 tone 表逐项符合 M3，自建 LCh 生成器还做对了 gamut 二分，AMOLED 叠加位置正确，浮球导航已经用 motion token 驱动且不触发重组。

**但「Expressive」目前只喂给了 M3 自带组件，项目自己的 UI 几乎全在体系外**，具体是三个断层：

1. **取色**：算法有三处可证伪的偏差（`error` 用错了色彩空间、12 个 fixed role 缺失、主色 chroma 无上限），加上对比度链路缺失（无 contrast level、文字用 alpha 稀释、`outline` 当文字色），以及三个用户可见的硬伤（系统栏图标跟系统而不跟 App 主题、公告卡在暗色下是 4 张近白卡、**浮动导航模式下贴底按钮被三键导航栏盖住**）。
2. **动效**：全仓 37 处动效点里，只有 `FloatingPanelNav` 一处读了 motion scheme；页面切换用的是 Navigation3 默认的 **700 ms 纯淡入淡出**（比 Expressive 的 150–500 ms 慢 2–4 倍，且完全没有方向感）；列表增删 0 处 `animateItem`；`Ambience` 的手写帧循环不遵守系统「移除动画」。
3. **形态**：M3 Expressive 最标志性的 shape morph / 35 形状库 / 新组件（ButtonGroup、SplitButton、FloatingToolbar、wavy progress、LoadingIndicator）在本版本依赖里**全部可用**，但一处未用。

**建议先做的 6 件事**（合计 ≈ 30 行改动，风险极低，收益立刻可见）：

| # | 事项 | 位置 |
|---|---|---|
| 1 | `NavDisplay` 传 `transitionSpec`/`popTransitionSpec`，700 ms → 150/450 ms expressive + 方向 | `app/ui/VectorApp.kt:104` |
| 2 | `AmbientSurface` 尊重系统动画缩放（读 `coroutineContext[MotionDurationScale]`） | `ui/ambience/AmbientSurface.kt:56` |
| 3 | 状态头颜色走 effects spec、形状走 spatial spec，删掉 `tween(420)` | `ui/StatusHeader.kt:121,282` |
| 4 | 有 key 的列表加 `Modifier.animateItem()` | `ui/logs/LogsScreen.kt:510` 等 |
| 5 | 去掉次级文字的 `copy(alpha=…)`、`outline` 当文字色、公告卡硬编码色 | 见 §4 C2/C3 |
| 6 | 补齐 12 个 fixed role + 修正 `error` 色相 | `ui/theme/SeedScheme.kt` |
| 7 | 浮动导航模式下给 `NavDisplay` 补 `navigationBarsPadding()`（这条是 bug 修复，见 C17） | `app/ui/VectorApp.kt:104` |

---

## 二、标尺：M3 Expressive 在「取色」与「动效」上到底说了什么

### 2.1 取色

**角色体系**：M3 有 26 个标准色彩角色，另加 **add-on 角色**：5 个 `surfaceContainer*`、`surfaceDim`/`surfaceBright`，以及 12 个 fixed 角色（`primaryFixed`/`primaryFixedDim`/`onPrimaryFixed`/`onPrimaryFixedVariant` × primary/secondary/tertiary）。

- fixed 角色的官方定义是「**在浅色/深色主题下保持同一 tone** 的填充色，用于需要在主题切换时保持不变的品牌块」，官方同时**明确警告**它不随主题变、有对比度风险（[Color roles](https://m3.material.io/styles/color/roles)）。
- `surfaceContainer` 五级是「按强调度命名的容器色」，官方从 2023-02 起就**用 tone 型角色取代了 +1~+5 elevation 混色**；Compose 从 1.3.0-alpha02 起组件默认改用 `surfaceContainer*`（[Color system](https://m3.material.io/styles/color/system)、[release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)）。
- 官方 tone 表（MCU `color_spec_2021`，[源码](https://github.com/material-foundation/material-color-utilities/blob/main/typescript/dynamiccolor/color_spec_2021.ts)）并非只有 0/10/…/100：neutral 需要 `4, 6, 12, 17, 22, 24, 87, 92, 94, 95, 96, 98` 这些细档来服务 surface 层级；并且 **tone 是 contrast level 的函数**（`ContrastCurve`）。

**对比度**：官方只有 **三档**（Standard / Medium / High，语义分别是默认、≥3:1、≥7:1），MCU 内部刻度是 `-1 … +1`（四个锚点 -1 / 0 / 0.5 / 1）。**Compose 至今没有 contrastLevel 入口**：对 `1.5.0-alpha25` 与 `1.6.0-alpha01` 的 AAR 全库检索 `contrastLevel` 均为 0 命中，只有 `dynamicLightColorScheme(Context)` / `dynamicDarkColorScheme(Context)` / `dynamicTonalPalette(Context)`。想要三档对比度必须自己接 [material-color-utilities](https://github.com/material-foundation/material-color-utilities)。

**动态色**：系统动态色的取色源是壁纸，系统进程完成量化/打分，App 只消费；Android 系统默认变体是 **TonalSpot**（primary C=36）。MCU 共 9 个 Scheme 变体：`Content / Expressive / Fidelity / FruitSalad / Monochrome / Neutral / Rainbow / TonalSpot / Vibrant`；`SchemeExpressive` 官方描述是「**有意脱离源色**」，2021 spec 下 primary 色相 **+240°**、C=40，2025 spec 改为保留源色相但 chroma 拉到 48/36 —— 即 **Expressive 的观感是「更艳」**，而 `dynamicLightColorScheme()` 拿不到这个艳度。

**HCT vs CIE LCh**：两者 **tone 完全相同**（都是 L\*），但 hue/chroma 分别是 CAM16 与 CIELAB 的量，**不可互换**。这一点直接决定了本项目的一个真实 bug（见 §4 C5）。

### 2.2 动效

**M3 Expressive 的核心变化是「用弹簧取代时长+曲线」**。官方原文：「In the expressive update, components and motion now use the motion physics system, which uses springs… The easing and duration system … is no longer maintained」（[Easing and duration](https://m3.material.io/styles/motion/easing-and-duration)）。

`MotionScheme` 有 6 个 spec，spatial（位置/尺寸/圆角/旋转）与 effects（颜色/透明度）分开。数值官方网页只给曲线不给参数，下表从 AOSP token 源码与本地 AAR 双重反查（[ExpressiveMotionTokens.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ExpressiveMotionTokens.kt)，与 `material3-android-1.5.0-alpha25` 的 classes.jar 一致）：

| scheme | spec | damping | stiffness | 官方 Web 等价曲线 | 名义时长 |
|---|---|---|---|---|---|
| expressive | default spatial | 0.8 | 380 | `0.38,1.21,0.22,1.00` | 500 ms |
| expressive | fast spatial | 0.6 | 800 | `0.42,1.67,0.21,0.90` | 350 ms |
| expressive | slow spatial | 0.8 | 200 | `0.39,1.29,0.35,0.98` | 650 ms |
| expressive | default effects | 1.0 | 1600 | `0.34,0.80,0.34,1.00` | 200 ms |
| expressive | fast effects | 1.0 | 3800 | `0.31,0.94,0.34,1.00` | 150 ms |
| expressive | slow effects | 1.0 | 800 | `0.34,0.88,0.34,1.00` | 300 ms |
| standard | default spatial | 0.9 | 700 | `0.27,1.06,0.18,1.00` | 500 ms |
| standard | fast spatial | 0.9 | 1400 | 同上 | 350 ms |
| standard | slow spatial | 0.9 | 300 | 同上 | 750 ms |
| standard | effects ×3 | 1.0 | 1600 / 3800 / 800 | 与 expressive 相同 | 200 / 150 / 300 ms |

> 一个容易踩的坑：`spring(dampingRatio = Spring.DampingRatioMediumBouncy)` **只改阻尼、不改刚度**，仍是默认 stiffness=1500 —— 会「又快又弹」读成抖动。expressive 的「弹」是 `0.6 / 800`：阻尼更高、刚度更低。

**Legacy 时长 token**（仍用于页面转场）：short1-4 = 50/100/150/200，medium1-4 = 250/300/350/400，long1-4 = 450/500/550/600，extraLong1-4 = 700/800/900/1000 ms。
**Easing**：Emphasized `(0.2,0,0,1)`（Compose 里与 Standard 相同，官方 Android 是两段式）、Emphasized decelerate `(0.05,0.7,0.1,1)`、Emphasized accelerate `(0.3,0,0.8,0.15)`。

**官方六种转场模式（现行命名，旧名已不再使用）**：Container transform / Forward and backward / Lateral / Top level / Enter and exit / Skeleton loaders。「shared axis」「fade through」在现行文档里已不作为 pattern 名出现（[Transitions](https://m3.material.io/styles/motion/transitions)）。

**Expressive 组件与形状库**：35 个 `MaterialShapes`（`Cookie9Sided`、`Clover4Leaf`、`Burst`、`Arch`…）+ `Morph` + `toShape()` 的 shape-morph 动画；14 个新增/更新组件含 Button groups、FAB menu、Loading indicator、Split button、Toolbars（[官方 blog](https://m3.material.io/blog/building-with-m3-expressive)）。

**无障碍**：Compose 已把系统 `ANIMATOR_DURATION_SCALE` 接进 `MotionDurationScale`（公开接口 `androidx.compose.ui.MotionDurationScale`，`WindowRecomposer` 持续 collect 系统设置），所有走 `AnimationSpec` 的动画自动合规；但**手写帧循环不受影响**。另需注意：`LocalMotionDurationScale` **不是公开 API**（对 compose-ui 1.12.0-beta01 全库检索 0 命中），要读缩放请在协程里取 `coroutineContext[MotionDurationScale]?.scaleFactor`。

---

## 三、现状评估

### 3.1 已经做得好、不必再提（避免重复劳动）

1. **`MaterialExpressiveTheme` + `MotionScheme.expressive()`**（`app/ui/theme/Theme.kt:59-66`）——库内组件动画（BottomSheet、TabRow、FAB、Switch、NavigationSuiteScaffold）自动合规。
2. **36/48 个角色全量显式覆盖，tone 表与 M3 逐项一致**（`ui/theme/SeedScheme.kt:80-157`）：`surfaceContainerLowest/Low/Container/High/Highest` = 100/96/94/92/90（浅）与 4/10/12/17/22（深）、`surfaceBright/Dim`、`inverseSurface/OnSurface`、`inversePrimary`、`outline`/`outlineVariant`、`surfaceTint`、`scrim` 全对。
3. **gamut 处理正确**：保 tone、保 hue，二分求最大 in-gamut chroma，而不是裁剪 RGB 通道（`ui/theme/SeedScheme.kt:221-249`）。
4. **对比度与种子无关**：主配对实测 ≥4.5:1（默认种子 primary/onPrimary 浅 6.46、深 7.72；surface/onSurface 16.3/14.3）。
5. **AMOLED 叠加位置正确**：在动态/种子 scheme 之后 `copy` 表面角色，壁纸强调色完整保留（`ui/theme/AmoledScheme.kt:17-27`）。
6. **色轮在 LCh 空间而不是 HSV**，32 万像素渲染放 `Dispatchers.Default` 并按 tone 缓存（`ui/ColorWheel.kt:42-68`）。
7. **浮球导航真用了 motion token**，且在 `absoluteOffset` / `graphicsLayer` 里读动画值，拖拽不触发重组（`ui/navigation/FloatingPanelNav.kt:146-156`、`:381`）。
8. **日志等级色语义化**（`error/tertiary/primary/outlineVariant` + 字母/形状冗余，`ui/logs/LogRows.kt:462-479`）、**WebView/HTML 全链路主题化**（CSS 从 ColorScheme 生成 + `color-scheme` + 强制 uiMode，`ui/store/StoreHtml.kt:172-245`）。
9. **触觉分级到位**（20 处：LongPress 开弧、SegmentTick 划色轮、Confirm 选中…）。
10. **状态头「形状+动效+颜色」三重编码的设计意图**（`ui/StatusHeader.kt:69-72` 注释）方向完全正确——只是实现没跟上（见 §5 M5/M11）。

### 3.2 取色链路

```
SettingsRepository(dynamic_color 默认 true, seed_color, amoled_black)
        │
        ├─ dynamic && API≥31 ─→ dynamicDark/LightColorScheme(context)   ← 系统壁纸（TonalSpot）
        └─ 否则 ─────────────→ SeedScheme.of(seed, dark)                ← 自建 LCh ramp
        │
        └─ dark && amoled ─→ toAmoled() 压黑表面
        │
        └─ MaterialExpressiveTheme(colorScheme, PixelShapes, MotionScheme.expressive())
```

结构正确，问题都在细节：动态分支**不缓存**、种子分支缺 fixed 角色、两条路径的 fixed 来源不一致、无对比度档位、切换无过渡。

### 3.3 动效现状（量化）

- 全仓 37 处动效点（含 `app/` 与 `manager-ui/` 两份副本）；**只有 2 个文件共 4 行读了 `MaterialTheme.motionScheme`**。
- 硬编码时序：`tween(420)`（状态头颜色 + 圆角）、`tween(1900)`（呼吸）、`tween(1600)`（loading 旋转）、`tween(320)/tween(180)`（语言切换）、`tween(320)`（splash）、`spring(MediumBouncy)`（语言勾选）。
- 页面转场：`NavDisplay` 未传 spec → **默认 `fadeIn(tween(700)) togetherWith fadeOut(tween(700))`**（nav3 `NavDisplay.android.kt:32-39`），无位移、无方向、无共享元素。
- `animateItem` / `animateContentSize` / `SharedTransitionLayout` / `sharedElement` / `LookaheadScope`：**0 处**。

---

## 四、取色改进

### C1（P0）系统栏图标明暗跟「系统」而不跟 App 主题

- **现象**：四个 Activity 都调无参 `enableEdgeToEdge()`（`app/ui/MainActivity.kt:36`、`app/ui/screens/logs/RunLogHistoryActivity.kt:57`、`app/ui/firmware/FirmwareSelectActivity.kt:78`、debug `DemoActivity.kt:81`）。无参版本的 `detectDarkMode` 读的是**系统** night mode，且只在 configuration 变化时重跑；App 内 `ThemeMode` 切换是纯 Compose 状态，不触发 configuration 变化。
- **后果**：系统深色 + App 选 Light → 白图标压在浅色背景上几乎看不见；系统浅色 + App 选 Dark/AMOLED → 黑图标压在深色上。这是「设置里切一下主题就能复现」的硬伤。
- **改法**：在 `VectorTheme` 里按 resolved `dark` 设置（约 10 行）：

```kotlin
val view = LocalView.current
val window = (view.context as? Activity)?.window
LaunchedEffect(dark) {
    window ?: return@LaunchedEffect
    WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
    }
}
```

### C2（P0）文字用 alpha 稀释，浅色下跌破 4.5:1

- **现象与实测**（默认种子，sRGB 相对亮度复算）：

| 位置 | 代码 | 浅色对比度 |
|---|---|---|
| `ui/ApiBadge.kt:31` | `onSurfaceVariant.copy(alpha = 0.7f)`（8sp） | **3.97:1** ❌ |
| `ui/logs/StackTrace.kt:175` | `onSurfaceVariant.copy(alpha = 0.7f)`（11sp） | **3.97:1** ❌ |
| `ui/StatusHeader.kt:201` | `onContainer.copy(alpha = 0.62f)`（24sp 品牌字，叠在动态渐变+ambience 上） | 4.39 / **3.13:1**（Neutral）⚠️ |
| `ui/logs/LogRows.kt:204,418` | 时间戳用 `outline`（M3 定义里 outline 是**边框**角色） | 4.27 / **3.85:1** ❌ |

- **对照 M3**：`onSurfaceVariant` 本身就是「已降权但仍满足 4.5:1」的角色，再乘 alpha 等于双重降权；层级应该用 role 表达（`onSurface` → `onSurfaceVariant` → 边框色），颜色不承载可用性信息的部分才允许 alpha。
- **改法**：删掉 0.7 的 alpha；`muted` 改 `onSurfaceVariant`；品牌字改 0.75 并保证底色不透明（或直接全不透明、靠字号区分）。并把「文字色不得用 alpha」写进 `docs/UI-GUIDELINES.md` §3。

### C3（P0）两处硬编码色在暗色/AMOLED 下破色

- **公告卡**（`app/ui/components/AnnouncerCard.kt:147-171`）：四个等级写死浅色套色（`#FFF3E0/#8A4B00`、`#FFEBEE/#9B1C1C`、`#E8F0FE/#1B3A6B`、`#F1F3F6/#33383D`）。暗色模式下变成黑底上 4 张近白卡（对 surface 约 16:1 反差）。改成 `@Composable fun styleOf(level)` 映射到 `primaryContainer / tertiaryContainer / errorContainer / surfaceContainerHigh`——仓库别处（`LogRows`、`StatusHeader`）本来就是这么做的。
- **RootFlow 成功徽章**（`app/ui/screens/rootflow/RootFlowScreen.kt:477-480`）：`Color(0xFF4CAF50).copy(alpha=0.15f)` 底 + `#2E7D32` 字，实测暗色 **2.90:1**、浅色 4.27:1，都不达标。改 `primaryContainer`/`onPrimaryContainer`（13.3/7.2）。

### C4（P1）12 个 fixed role 缺失：种子路径永远是基线紫

- **现象**：`SeedScheme` 只填 36 个角色，漏掉 `primaryFixed / primaryFixedDim / onPrimaryFixed / onPrimaryFixedVariant` × primary/secondary/tertiary。未传的参数走 `lightColorScheme()/darkColorScheme()` 的默认值 = **M3 基线紫**（`primaryFixed = #EADDFF`、`primaryFixedDim = #D0BCFF`、`secondaryFixed = #E8DEF8`、`tertiaryFixed = #FFD8E4`）。
- **额外的不一致**：动态路径**是填了**的（`DynamicTonalPalette.android.kt` 从 `system_primary_fixed` 读），所以「开动态色 = fixed 跟壁纸，关动态色 = fixed 永远紫」。
- **现状风险**：当前库内组件尚未消费 fixed（对 alpha25 AAR 检索只有声明），所以还没人看见——但任何新代码写 `colorScheme.primaryFixed` 就立刻露馅；Expressive 的「vibrant color / 品牌块」正好鼓励用它。
- **改法**（两个分支各加 12 个参数，tone 映射与官方 token 一致）：

```kotlin
primaryFixed = primary[90],  primaryFixedDim = primary[80],
onPrimaryFixed = primary[10], onPrimaryFixedVariant = primary[30],
// secondary/tertiary 同理（light 与 dark 取值相同——fixed 按定义不随主题变）
```

### C5（P1）`error` 用错了色彩空间：红色偏洋红

- **现象**：`val error = Ramp(25f, 84f)`（`ui/theme/SeedScheme.kt:76-78`）。25 是 **CAM16/HCT 的 hue**，直接塞进 CIE LCh 生成的是 `#BF0031`（数值复算一致）。
- **对照官方**：M3 error tone40 = `#B3261E`，其 **CIE Lab hue = 36.4**、C=68.2（复算）。项目当前色相偏差 **≈11°**，肉眼可辨（红橙 vs 洋红）。
- **改法**：`Ramp(36.4f, 68f)`，或直接写死官方六档（`#B3261E / #8C1D18 / #F9DEDC / #410E0B …`）。这也是「HCT 的 hue 不能直接填 Lab」的现成案例，值得写进代码注释。

### C6（P1）primary chroma 只有下限没有上限：高饱和种子明暗不一致

- **现象**：`chroma.coerceAtLeast(48f)`（`ui/theme/SeedScheme.kt:71`）。种子很艳时直接用种子 chroma：violet 预设 `#7C4DFF`（C=101.7）→ 浅色 primary `#6238E7`（荧光紫），而 tone 80 被 gamut 压到 C=37.8 → `#D5BAFF`（薰衣草）。同一主题切换明暗，鲜艳度掉一半。blue 预设同理（82.8 → 33.5）。
- **对照 M3**：HCT 恒定 chroma 的目的正是让两端观感一致（TonalSpot 36；Expressive 2025 spec 48/36）。
- **改法**：`coerceIn(48f, 72f)` 先止血；若要「Expressive 的艳」，按 C15 换成 MCU 的 `SchemeExpressive` 并让它自己 clamp。

### C7（P1）AMOLED 漏压 3 个角色，且容器层级不可辨

- `surfaceBright` 未覆盖（仍是 tone 24 ≈ `#323B3D`，纯黑面上的灰块）、`surfaceVariant` 未覆盖（`#374A4E`，Markdown 代码块/引用/表头都是它，AMOLED 下「黑底贴浅灰块」）、`outlineVariant` 对 `#1C1C1C` 只有 **1.82:1**（分隔线基本消失）。
- 五档容器 `#000/0A/10/16/1C` 相邻对比仅 **1.04–1.06:1**，`surfaceContainer` 对背景 1.10:1 —— 等于放弃「用容器色分层」这套语言。
- **改法**：① 补 `surfaceBright = #1C1C1C`、`surfaceVariant = #1A1A1A`、`outlineVariant` 提到 `#2E2E2E` 以上；② 给卡片加 `outlineVariant` 描边（M3 Expressive 本身也在向「shape + 明确描边」表达层级走）；③ 长期把 5 档改成按 L\* 等距（tone 3/6/9/12/15）而不是等距 sRGB 灰度。**别动** `surface`/`background` 的真黑，那是 AMOLED 的意义。

### C8（P1）没有 contrast level，也没读系统高对比度

- 全仓无 `contrastLevel` / `isHighTextContrastEnabled` / `LocalAccessibilityManager`。官方是三档（Standard / Medium 3:1 / High 7:1）。
- **改法**（不引库的最小实现）：给 `SeedScheme.of(seed, dark, contrast)` 加第三参，按档位整体挪 tone（例如高对比：`onSurfaceVariant 30→20`、`outline 50→40`、`onXContainer 10→5`、`surfaceContainerHigh` 压一档）；读取侧 API 36+ 用 `AccessibilityManager.isHighTextContrastEnabled()`，27–35 用 `Settings.Secure.getInt(cr, "high_text_contrast_enabled", 0)`（注意该常量不是公开字段，只能写字面量）。UI 入口放 `AppearanceSheet` 颜色段、紧邻 AMOLED 开关。
- 若愿意引 `material-color-utilities`，直接 `SchemeTonalSpot(hct, isDark, contrastLevel)` 省掉全部推导，同时解决 C4/C5/C6/C15。

### C9（P2）切主题/切色没有过渡

- `Theme.kt` 全文件无动画，`ColorScheme` 一变整屏瞬跳。Material You 换壁纸/换色有 ~300 ms 淡入，本项目主题注释也写着「让变化像是被引起的」——这里恰恰是被「瞬间重置」的。
- **改法**：把 `MaterialExpressiveTheme` 包进 `Crossfade(targetState = scheme, animationSpec = tween(300))`（最省、视觉可接受）；或只对 8 个高频角色做 `updateTransition`。注意 `Crossfade` 期间两套主题树并存，有 ambience 的页面会双帧循环（配合 M12 压短/暂停）。

### C10（P2）动态 scheme 未 `remember`：每次重组都重建并整树失效

- `app/ui/theme/Theme.kt:52-54` 两条动态分支没有 `remember`，而种子分支有（`:55`）。`ColorScheme` 未重写 `equals`，`MaterialTheme` 用 `staticCompositionLocalOf` → 值不等会让整棵子树失效。改 `remember(dark) { dynamicDarkColorScheme(context.applicationContext) }`。

### C11（P2）色轮经 tone60 ARGB 往返，拇指会回弹

- `AppearanceSheet.kt:265` 把指针位置换成 `wheelColor(h, c).toArgb()` 存成种子，`:260` 再用 `Color(seed).toWheel()` 反算。轮缘处出 gamut → chroma 被截：cyan 方向请求 110 实际只剩 ~34，松手后拇指从 100% 半径跳回 31%。
- **改法**：种子存 (hue, chroma) 两个 Float（或 packed long），`SeedScheme.of` 加 `of(hue, chroma, dark)` 重载跳过 `toLch()`；拖动中只更新内存 flow，`onDragEnd` 才落盘（现在每次 pointer move 都 `prefs.edit().apply()`）。

### C12（P2）`TonalPreview` 名不副实

- 动态模式预览的是 10 个**混排角色**（primary、onPrimaryContainer、primaryContainer…），不是 tonal ramp；种子模式把 chroma **硬编码成 48**（`AppearanceSheet.kt:338`），与真实 primary ramp（`max(seedC, 48)`）不一致——violet 预设预览 `#6A509E` vs 实际 `#6238E7`。
- **改法**：暴露真实 ramp（`SeedScheme.primaryRamp(seed)`），按 primary/secondary/tertiary/neutral 分组展示 5 条 ramp 并在色块下标 tone 数字。

### C13（P2）API<31 + 默认 `dynamicColor=true` → 一行色块全未选中

- 动态色默认开（`SettingsRepository.kt:30`），但动态圆点只在 API≥31 显示（`AppearanceSheet.kt:194,206`）；预设选中条件是 `!dynamicColor && …`。于是 Android 11 及以下默认状态下「没有任何来源被选中」，用户看不出主题从哪来。改成 `val effectiveDynamic = dynamicColor && supportsDynamic` 并据此判断选中。

### C14（P2）首帧可能闪白/闪黑

- `app/src/main/res/values/themes.xml` 没有 `windowBackground`、没有 `values-night/themes.xml`、没有 `windowSplashScreenBackground`；平台 splash 用系统 `?android:colorBackground`，而 `setKeepOnScreenCondition { false }` 让它在首帧就撤。于是「App Dark + 系统 Light」会闪白一帧。
- **改法**：`values/themes.xml` 与 `values-night/themes.xml` 各补 `android:windowBackground`（浅色 ≈ `#F0FBFE`、深色 `#0B1517`、AMOLED 可给 `#000000`），API 31+ 再补 `windowSplashScreenBackground`。

### C15（P2）「从图片取色」和「Expressive 的鲜艳度」都没有入口

- 现在只有「系统壁纸 / 8 预设 / 色轮」。M3 的图片取色链路是：量化（`QuantizerCelebi`）→ `Score` 打分 → 生成 scheme；Android 侧没有现成 `ImageUtils`，需自己解码 Bitmap 取像素再喂 MCU。约 150 行 + 依赖，排 P2。
- 另一条更便宜的「Expressive 感」路线：**用 MCU 的 `SchemeExpressive` 生成种子方案**（2025 spec：primary C=48/36）。比现在「无上限」的做法既更艳又更稳（同时修 C6）。若不想引库，也可以把 `SeedScheme` 的 chroma 曲线按 tone 调整（低 tone 少请求、高 tone 多请求），逼近「恒定 chroma」的观感。

### C16（P2）`docs/UI-GUIDELINES.md` 已过期

- §2.2/§5.7 仍写着「本仓库 Material3 版本不能用 `SegmentedButton`」，但 `AppearanceSheet.kt:40-43,165-182` 正在用，且编译通过。升级到 1.5.0-alpha25 后该限制已消失，文档要改，否则后续 PR 会照着旧规范把它拆掉。

### C17（P0，实测复现）浮动导航模式下贴底元素被系统三键导航栏盖住

- **现象**（SM-S9280 真机，`navigation_mode=0` 三键导航，`material3 1.5.0-alpha25`）：开启设置里的浮动导航后，日志页底部「Soft reboot (load modules)」pill 压在系统导航栏区域上，`III 〇 ◁` 三个虚拟键直接画在按钮上面。截图证据：`/tmp/rms24_float.png`（本次会话采集）。
- **根因**：不是 `FloatingPanelNav` 没看 inset——它读了 `WindowInsets.safeDrawing` 并把球钳在 `[safeDrawing, 窗口]` 里（`ui/navigation/FloatingPanelNav.kt:164-197`，注释也写明了「scaffold 在 None 模式下不消费 inset，所以要自己读」）。真正的问题是**页面内容**没人补底部 inset：
  - 浮动导航走 `NavigationSuiteType.None`，`NavigationSuiteScaffold` 对它 `consumeWindowInsets(NoWindowInsets)`、也不为底部系统栏预留高度（adaptive-navigation-suite `NavigationSuiteScaffold.kt:1523-1553`）。内容于是铺满整窗，贴底元素落在系统导航栏后面。
  - 日志页的 pill 直接 `align(Alignment.BottomCenter).padding(bottom = 16.dp)`（`app/ui/screens/logs/LogsScreen.kt:186`），没有任何 inset；非浮动模式下是这个脚手架替它留出了高度，所以一直没暴露。
  - 同类：`manager-ui` 的日志跳转 FAB 行 `Modifier.align(Alignment.BottomEnd).padding(12.dp)`（`ui/logs/LogsScreen.kt:534`）；**上游 Vector 同一处代码至今没有补 inset**（`JingMatrix/Vector` `manager-ui/.../logs/LogsScreen.kt:549`）。
- **上游其实修过同一类问题**：`JingMatrix/Vector` PR **#885** / commit `1c1b829f`（2026-08-07，标题 *"Keep the bars docked to the window out of the navigation bar"*）用**同样的药方**（`navigationBarsPadding()`）修了 Home 信息流的底行 + 三个详情页的 docked bar，commit message 对根因的描述与本节一致：
  > "…the navigation container is hidden away from a panel root and NavigationSuiteScaffold consumes NoWindowInsets while hidden: nothing above the screen has taken the system bars. With three-button navigation that is 48dp…"
  两个可复用的实现细节：① docked bar 要把 padding 加在 **Surface 内部**（否则填充色在导航栏上方断掉，露出背景色带）；② Home 设了 `contentWindowInsets = 0` 让头部穿到状态栏下，于是底部要按 `ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Bottom)` 单独取回来。
  本 fork 仍有该 bug 的原因：`manager-ui` 是 8 月 17 日从 Vector 导入的（比 #885 晚），`RepoDetailsScreen` 自带那两处调用，但 #885 修的 Home/Scope/Troubleshoot/FrameworkUpdate 在本 App 里不存在；而本 App 的面板（RootFlow、Settings、Logs）**不是 Scaffold**，没有任何一层取过底部 inset —— 所以既要按上游原则补局部，又需要一个外壳级的兜底。
- **修复**（已在本次提交前落地，见 git 工作区）：
  1. `app/ui/VectorApp.kt:104`：给 `NavDisplay` 加 `modifier = Modifier.navigationBarsPadding()`。导航栏/导航轨模式下脚手架已消费底部 inset，该 padding 为 0，所以无条件加是安全的；浮动模式（以及导航轨模式）下它正好补上缺的那一段，**所有页面的贴底内容一次性修好**。浮动球是它的兄弟节点，仍按 `safeDrawing` 自己定位，不受影响。
  2. `app/ui/screens/logs/LogsScreen.kt:186` 与 `ui/logs/LogsScreen.kt:534`：再各自加一层 `navigationBarsPadding()`。外层加了之后内层读到的是已消费的 0，不会双倍；这样做是为了让这两处**不依赖外壳实现**，也方便把 manager-ui 的改动原样提给上游 Vector。
- **对照 M3**：edge-to-edge 下「内容可以画到系统栏后面，但可交互元素必须留在安全区内」是 Android 15+ 的默认契约；浮动导航等于放弃了底部导航容器的 inset 预留，所以必须由内容自己补。
- **验证**：三键导航 + 浮动导航 → pill 在导航栏上方 16 dp；手势导航 → 不出现多余空白；关掉浮动导航（底部条模式）→ 视觉与改前完全一致（inset 已被脚手架消费为 0）。

---

## 五、动效改进

### M1（P0）页面转场：700 ms 纯淡入淡出 → expressive + 方向

- **现象**：`NavDisplay` 未传 spec（`app/ui/VectorApp.kt:104-113`），默认 `fadeIn(tween(700)) togetherWith fadeOut(tween(700))`。Expressive 的页面级时长是 150–500 ms；且顶级目的地之间官方推荐 **Top level**（快出快入、无分组）或 **Lateral**（同级同向滑入），进二级页用 **Forward and backward**（推进式滑动）。
- **改法**（spec 必须在非 `@Composable` 的 lambda 里用，所以先在外层取好）：

```kotlin
val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
val fastFx  = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
val fx      = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

NavDisplay(
    backStack = navigator.backStack,
    onBack = { navigator.back() },
    transitionSpec = {                       // 同级/顶级：快、无方向（Top level）
        (fadeIn(fastFx) + scaleIn(fastFx, initialScale = 0.98f))
            .togetherWith(fadeOut(fastFx))
    },
    popTransitionSpec = {                    // 进二级：Forward and backward
        (fadeIn(fx) + slideInHorizontally(spatial) { it / 8 })
            .togetherWith(fadeOut(fx))
    },
    sizeTransform = null,
    entryDecorators = listOf(...),
    entryProvider = entryProvider { registerRoutes(navigator) },
)
```

- 具体参数名以 nav3 1.1.5 的 `NavDisplay` 重载为准（`transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec` / `sizeTransform`；也可用 `NavDisplay.transitionSpec {}` 给单条路由打 metadata）。工作量 1 文件 ~12 行，风险低；**必须真机看** `SettingsScreen`（995 行）这类重页，避免「动画结束内容还没画完」。

### M2（P0，无障碍）`AmbientSurface` 的手写帧循环不遵守「移除动画」

- **现象**：`ui/ambience/AmbientSurface.kt:56-71` 用 `withFrameNanos` 自驱，完全不经过 `AnimationSpec`；系统「移除动画」后背景粒子照跑，而其它 Compose 动画都已定格。另外四个 renderer 的 `isAnimating` 全是 `get() = true`，`:59` 的「停车」分支实际是死代码。
- **改法**（注意：`LocalMotionDurationScale` 不是公开 API，别用；正确读法是协程上下文里的 `MotionDurationScale`）：

```kotlin
LaunchedEffect(kind) {
    var last = 0L
    while (true) {
        // MotionDurationScale 在 LaunchedEffect 的协程上下文里（由 WindowRecomposer 注入，
        // 且实时跟随系统「动画时长缩放」变化）。注意 withFrameNanos 的 lambda 不是挂起函数，
        // coroutineContext 只能在循环体里读，不能读进 lambda 内部。
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (!renderer.isAnimating || scale == 0f) {
            if (last == 0L && scale == 0f) {
                // 系统关闭动画：画一帧静态画面，然后停在这个状态
                renderer.update(16f, canvasSize)
                frame += 1f
            }
            last = 0L
            awaitFrame()          // 仍每帧醒一次：设置变化后能立刻恢复
            continue
        }
        withFrameNanos { now ->
            val dt = if (last == 0L) 16f else (now - last) / 1_000_000f
            last = now
            renderer.update(dt.coerceAtMost(64f), canvasSize)
            frame += 1f
        }
    }
}
```

（`scale == 0f` 分支里 `last = 0L` 会让循环每帧都重新判断一次；如果觉得「每帧醒一次」浪费，可以改成 `Settings.Global` 的 `ContentObserver` 驱动一个 state，只在变化时重建 `LaunchedEffect`。）

顺带把 `isAnimating` 真正接上（例如 `speed > 0f`），并在 `AmbienceSettings` 加「静止」档（当前只能在「4 档全速」和「完全空背景」之间二选一）。

### M3（P0）全仓 0 处 `animateItem()`

- Lazy 列表 20+ 处，增删/移动全硬切。`animateItem()` 默认就是 spring 系，加一行即合规。
- 先做已有 key 的：`ui/logs/LogsScreen.kt:510`、`ui/store/RepoDetailsScreen.kt:559`。`app/ui/screens/logs/LogsScreen.kt:146` 的 `itemsIndexed(shown)` **没有 key**，必须先补再开动画（否则错乱会被放大）。
- 日志页 2 s 轮询追加，建议只开 placement、别开 fadeIn，否则「更吵」。

### M4（P1）33/37 处动效在体系外：替换表

把硬编码时序换成 motion scheme，是本项目「一次性把 Expressive 落地」性价比最高的一步：

| 现值 | 位置 | 建议 |
|---|---|---|
| `tween(420)` 颜色 | `ui/StatusHeader.kt:129,140` | `motionScheme.defaultEffectsSpec<Color>()` |
| `tween(420)` 圆角 | `ui/StatusHeader.kt:289` | `motionScheme.defaultSpatialSpec<Float>()` |
| `infiniteRepeatable(tween(1900))` 呼吸 | `ui/StatusHeader.kt:293-300` | 删除，改一次性确认（M6） |
| `tween(2000, LinearEasing)` 齿轮 | `ui/StatusHeader.kt:324-326` | 保留线性（机械旋转合理），但降为 `tween(1200)` 更轻 |
| `tween(1600)` loading 旋转 | `app/ui/components/VectorSnackbar.kt:157-158` | 换成 `LoadingIndicator`（M10） |
| `tween(320)/tween(180)` 语言切换 | `app/ui/theme/AppLocale.kt:81-82`、`ui/locale/AppLocale.kt:70-71` | 前 `fastFx`、后 `fastSpatial` |
| `tween(320)/tween(220)` splash | `app/ui/screens/splash/SplashScreen.kt:116-136` | **不要动**（与 `ART_MS=320` 硬闸门耦合，注释已解释） |
| 默认 spring（1.0/1500） | `ui/navigation/PanelBar.kt:180-181`、`ui/navigation/FloatingPanelNav.kt:366`、`ui/appearance/AppearanceSheet.kt:289`、`ui/logs/LogsScreen.kt` 等 20+ 处 | 按通道：空间 `defaultSpatial/fastSpatial`、颜色 alpha `fastEffects` |
| `spring(MediumBouncy)` 勾选 | `ui/locale/LanguageSheet.kt:147-151` | `fastSpatialSpec<Float>()`（0.6/800 才有正确的弹感） |

建议先抽一个门面，避免 20 处各写一遍：

```kotlin
object VectorMotion {
    @Composable fun spatial()     = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    @Composable fun fastSpatial() = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    @Composable fun effects()     = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    @Composable fun fastEffects() = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
}
```

### M5（P1）状态头把「颜色」和「形状」塞进同一个 `tween(420)`

- 颜色属 effects 通道（≈200 ms 收敛），形状/尺寸属 spatial 通道（≈500 ms、带轻微 overshoot）。现在同一个 420 ms 线性推进，抹掉了两套物理的区别；420 也不在任何 token 上。
- 分开后观感会明显「贵」：颜色先到、形状随后弹到位。

### M6（P1）徽章的「呼吸」是永久无限动画

- `ui/StatusHeader.kt:293-300`：`rememberInfiniteTransition` 无条件创建（非 Active 也在跑），Active 时 1.00↔1.05 / 1900 ms 往返，界面永远有 3% 缩放抖动。
- **对照 M3**：Expressive 的「达成/确认」是**一次性过冲**，持续脉动只属于 loading 态。
- **改法**：改成 tone 变 Active 时的一次性确认：

```kotlin
val pop = remember { Animatable(1f) }
LaunchedEffect(tone) {
    if (tone == StatusTone.Active) {
        pop.snapTo(1f)
        pop.animateTo(1.12f, MaterialTheme.motionScheme.fastSpatialSpec())  // 0.6 阻尼自带回弹
        pop.animateTo(1f, MaterialTheme.motionScheme.fastSpatialSpec())
    }
}
```

### M7（P1）同一屏两套物理

- 浮球开弧用 `fastSpatialSpec`（0.6/800，果冻感），同屏 `FanItem` 高亮 `animateFloatAsState` 用默认 1.0/1500（像开关）；`PanelBar` 的抬起/让位也是默认 spring。一次交互里出现两种材质。
- 改法：`FanItem` 抬起与 `PanelBar` 让位都换 token（各 1 行）；`PanelBar` 的隐藏 alpha 走 `fastEffectsSpec`。

### M8（P1）折叠面板硬切，且箭头有两种实现

- 仓库发布说明：chevron 有旋转动画（`ui/store/RepoDetailsScreen.kt:644`），正文却是 `if (notesOpen)` 直接出现（`:692`）；模块行（`ui/ModuleRow.kt:228,238`）和 FAQ（`app/ui/screens/about/AboutScreen.kt:484,500`）连箭头都是**换字形**而不是旋转。
- 改法：容器加 `Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())`，箭头统一 `animateFloatAsState` + `Modifier.rotate`。`animateContentSize` 目前 0 处使用。

### M9（P1）向导/状态机的步进没有方向

- `app/ui/screens/onboarding/OnboardingScreen.kt:85` 是 `when (step)` 整页硬切；`RootFlowScreen.kt:456-490` 的「播放→转圈→完成」也是瞬间替换。
- M3 对 step-by-step 流程指定的转场是 **Forward and backward**（推进/退回），按钮内部状态变化属 container transform。
- 改法：向导包 `AnimatedContent(targetState = step)`，按 `targetState > initialState` 选正/反向位移；注意第 0 页是长文 `WikiContent()` + `verticalScroll`，双份 compose 会掉帧，所以只用「淡入+小幅位移」，**别加 SizeTransform**。

### M10（P1）进度/加载指示器仍是旧版

- 全部 10+ 处用 `CircularProgressIndicator` / `LinearProgressIndicator`（`RootFlowScreen.kt:474,735,774`、`ui/logs/LogsScreen.kt:404,444`、`ui/store/RepoDetailsScreen.kt:215,402,407,421`、`app/ui/screens/web/WebScreen.kt:266`…）。
- **可用性已核实**：`LinearWavyProgressIndicator` 在 alpha25 **已稳定、无需 opt-in**（零风险，建议先全换）；`LoadingIndicator` / `ContainedLoadingIndicator` / `MaterialShapes` / `ToggleButton` 需要 `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`；`ButtonGroup` / `SplitButton` / `FloatingToolbar` **已 promote、无需 opt-in**（逐类二进制检索确认）。
- Expressive 的 loading indicator 是 shape-morphing（Circle ↔ Clover ↔ Burst），是最容易被用户感知的部件，建议先在 `RootFlowScreen` 的「正在提权」处试水。

### M11（P2）状态徽章的「形状」可以是真的 shape morph

- 现在 Active/Warning/Error 只是改圆角百分比（34/42/50，`ui/StatusHeader.kt:282-291`），边数与比例不变，三态在形状上几乎看不出差别——与注释「state is carried by shape and motion as well as hue」不符。
- M3 Expressive 的正解是 `MaterialShapes` + `Morph`（35 个归一化多边形，`androidx.graphics.shapes` 1.0.1 已在依赖里）：

```kotlin
val morph = remember { Morph(MaterialShapes.Square, MaterialShapes.Circle) }
val path = remember { Path() }
val shape = remember { GenericShape { _, _ -> morph.toPath(corner, path) } }
```

- 注意 `rememberAnimatedShape` 是 material3 的 **internal** API（`androidx.compose.material3.internal.AnimatedShapeKt`），不可直接用；Path 必须 `remember` 缓存。这会给 `docs/UI-GUIDELINES.md` 的「形状只用主题尺度」开一个例外，建议同时在规范里写明「brand shape 例外清单」。

### M12（P2）两处可量化的性能问题

- **Ambience 每帧分配**：`ui/ambience/SnowRenderer.kt:258,261` 在绘制循环里 `listOf(1f,-1f)` / `listOf(0.55f,-0.55f)`，上限 90 片时 ≈ 540 个 List/帧（120 Hz ≈ 6.5 万/秒）；`MatrixRenderer.kt:350-357` 每个字形都 `style.copy()` + `toString()`，12 列 × ~20 字 ≈ 每帧 1000+ 次分配。改成 `FloatArray` 常量 + 按 alpha 分档预建 `TextStyle`。
- **转场期双帧循环**：700 ms 淡入淡出期间离场/进场页同时 compose，各带一个 `AmbientSurface` → 2 个 Canvas 满帧重绘 + 2 份粒子模拟。压短转场（M1）即可缓解，彻底解法是给 `AmbientSurface` 加 `enabled` 参数，由 host 在转场期间传 `false`。

### M13（P2）两处可点文字没有按压反馈

- `ui/StatusHeader.kt:192-196,209-213` 用 `indication = null` 去掉了 ripple。正解不是去掉，而是给渐变头部包一层 `LocalRippleConfiguration`（`RippleConfiguration(color = onContainer.copy(alpha = .2f))`）。

### M14（结构性）同一件事两套实现，正在漂移

- `FloatingPanelNav`（app 468 行 / manager-ui 468 行）、`PanelBar`（448 / 516）、`NavPanels`、`LogsScreen` 都有两份副本；已出现漂移：app 版 `PanelBar` 少 expressive opt-in、app 版日志列表没有 key。
- 建议：UI 组件单一实现 + `VectorMotion` 门面，让「换 spec」变成 grep 可查的机械工作，而不是 20 处逐个找。

### M15（P2）Expressive 组件与字阶：可用但未用

| 能力 | 状态（alpha25 实测） | 建议用在哪 |
|---|---|---|
| `ButtonGroup` / `SplitButton` / `FloatingToolbar` | ✅ 无需 opt-in | 外观面板的亮度/语言分段（替 `SegmentedButton`）；浮球导航的替代形态 |
| `ToggleButton` / `LoadingIndicator` / `MaterialShapes` | ✅ 需 opt-in | 徽章 shape morph（M11）、加载态（M10） |
| `LinearWavyProgressIndicator` | ✅ 稳定 | 所有确定性进度条 |
| `Typography.*Emphasized`（headlineMediumEmphasized 等 15 个） | ✅ 已在 `Typography` 上 | 状态头品牌字、页面大标题、空状态标题（比 `SemiBold` 更「Expressive」） |
| `Shapes` 的 `largeIncreased / extraLargeIncreased / extraExtraLarge` 三档 | ✅ 库有，**项目未设** | 见 §6，新组件会回落到 M3 默认圆角，与 `PixelShapes` 不一致 |

---

## 六、设计规范层面的两个取舍（不是 bug，但要决策）

1. **「关节圆角 20→4」是自建视觉语言，不是 M3**。M3 的做法是「整块容器 + `surfaceContainer*` 分层 + shape 家族（8 档）」。20→4 的关节让每一行都是一个独立 Surface，优点是边界清晰；代价是：① 形状数量爆炸（每行 4 个角各自算），`animateContentSize`/shape morph 都无从谈起；② 与 M3 新组件的圆角尺度对不上（见下条）。建议**保留**这套语言（它已经是品牌识别），但在规范里明确「关节圆角只用于列表行」，并给卡片/面板/新组件留出 `MaterialTheme.shapes` 的通道。
2. **`PixelShapes` 只设了 5 档，漏了 3 档**。`Shapes` 在 1.5.0-alpha25 有 8 个字段（`extraSmall…extraExtraLarge` + `largeIncreased/extraLargeIncreased`），`PixelShapes` 只给了 8/12/20/28/32，**另外三档走 M3 默认**。Expressive 的新组件（大按钮、toolbar、split button）用 `largeIncreased/extraLargeIncreased` 派生的 `ButtonShapes`——于是新旧组件的圆角会不一致。补一句即可：

```kotlin
private val PixelShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
    largeIncreased = RoundedCornerShape(24.dp),      // 20 与 28 之间
    extraLargeIncreased = RoundedCornerShape(36.dp), // 与 32/48 衔接
    extraExtraLarge = RoundedCornerShape(48.dp),     // M3 规范值
)
```

---

## 七、落地路线

**迭代 1「低成本、高感知」（≈1–2 人日，全部低风险）**
M1 页面转场 → M2 移除动画合规 → M5 状态头分通道 → M7 同屏物理统一 → M3 `animateItem` → C1 系统栏图标 → C2/C3 对比度与硬编码色 → C16 更新规范文档。

**迭代 2「取色正确性」（≈2 人日）**
C4 fixed roles → C5 error 色相 → C6 chroma 上限 → C7 AMOLED 补压 → C13/C14 边界与首帧 → C9 主题过渡 → C12 真实 tonal preview。
建议在这一迭代里评估「是否引入 material-color-utilities」：一次解决 C4/C5/C6/C8/C15，代价是一个 ~50 KB 依赖 + 观感变化。

**迭代 3「Expressive 形态」（≈3–5 人日，需设计参与）**
M10 wavy/loading → M15 Expressive 组件与 Emphasized 字阶 → M8 折叠动画 → M9 向导共享轴 → M11 shape morph → M1 的 container transform（仓库卡 → 详情）→ §6 的形状体系决策。

---

## 八、验证清单（改完必做）

- [ ] 真机：暗色 + AMOLED + `fontScale 1.3`（甚至 1.5）下不截断/不重叠（`docs/UI-GUIDELINES.md:144` 的既有要求）
- [ ] 开发者选项「动画时长缩放 = 关闭」：所有页面切换、列表、ambience 都应静止但仍可交互
- [ ] 系统深色 + App Light / 系统浅色 + App Dark：状态栏与导航栏图标必须可辨（C1）
- [ ] 冷启动「App Dark + 系统 Light」：不应出现白闪（C14）
- [ ] 8 个预设 × 浅/深 × AMOLED：主配对对比度 ≥4.5:1；`error` 色不得偏洋红（C5）
- [ ] 色轮从中心拖到轮缘再松手：拇指不应回弹（C11）
- [ ] 切换主题时观察：不应瞬跳（C9），且不应出现「旧主题残留一帧」
- [ ] `fontScale 1.5` 下扇形导航标签（`FAN_LABEL_ROOM = 22.dp`）不越界（M10 的几何常量改实测）

---

## 附录 A：证据索引（关键行号）

| 主题 | 位置 |
|---|---|
| 主题装配 / motion scheme | `app/ui/theme/Theme.kt:39-66` |
| 种子生成器（角色表、chroma、error） | `ui/theme/SeedScheme.kt:64-78, 80-157, 162-169` |
| AMOLED 压黑 | `ui/theme/AmoledScheme.kt:17-27` |
| 动态色开关 / 默认值 | `app/data/repository/SettingsRepository.kt:30`、`ui/appearance/AppearanceSettings.kt:15` |
| 取色面板（预设/色轮/预览） | `ui/appearance/AppearanceSheet.kt:186-279, 320-364` |
| 色轮 | `ui/ColorWheel.kt:56-115, 142-167` |
| 状态头（颜色/形状/呼吸/齿轮） | `ui/StatusHeader.kt:121-142, 282-338, 386-395` |
| 页面转场 | `app/ui/VectorApp.kt:104-113` |
| 浮球导航（token 使用处） | `ui/navigation/FloatingPanelNav.kt:146-156, 366, 381` |
| 面板条拖拽 | `ui/navigation/PanelBar.kt:180-181, 269` |
| Ambience 帧循环 | `ui/ambience/AmbientSurface.kt:56-71, 117-121` |
| Ambience 每帧分配 | `ui/ambience/SnowRenderer.kt:258,261`、`MatrixRenderer.kt:350-357` |
| 折叠面板 / 箭头 | `ui/store/RepoDetailsScreen.kt:644,692`、`ui/ModuleRow.kt:228,238`、`app/ui/screens/about/AboutScreen.kt:484,500` |
| 向导步进 | `app/ui/screens/onboarding/OnboardingScreen.kt:85` |
| 硬编码色 | `app/ui/components/AnnouncerCard.kt:147-171`、`app/ui/screens/rootflow/RootFlowScreen.kt:477-480` |
| 文字 alpha / outline 当文字 | `ui/ApiBadge.kt:31`、`ui/logs/StackTrace.kt:175`、`ui/StatusHeader.kt:201`、`ui/logs/LogRows.kt:204,418` |
| 系统栏 / 首帧 | `app/ui/MainActivity.kt:36`、`app/src/main/res/values/themes.xml` |
| 浮动导航模式的底部 inset（C17） | `app/ui/VectorApp.kt:104`（`NavDisplay` 补 `navigationBarsPadding`）、`app/ui/screens/logs/LogsScreen.kt:186`、`ui/logs/LogsScreen.kt:534` |
| 规范文档 | `docs/UI-GUIDELINES.md`（§2.2 分段按钮禁令已过期） |

## 附录 B：参考资料

- M3 色彩角色与 surface/fixed 语义：<https://m3.material.io/styles/color/roles>
- M3 色彩系统（HCT、对比度三档）：<https://m3.material.io/styles/color/system>
- MCU 源码（tone/ContrastCurve/Scheme 变体）：<https://github.com/material-foundation/material-color-utilities>
- M3 动效总览（spring 体系）：<https://m3.material.io/styles/motion/overview>
- 官方 spring 曲线与名义时长表：<https://m3.material.io/styles/motion/overview/specs>
- 时长/缓动 token（官方宣告停更）：<https://m3.material.io/styles/motion/easing-and-duration/tokens-specs>
- 六种转场 pattern（现行命名）：<https://m3.material.io/styles/motion/transitions>
- Expressive 发布 blog（35 shapes、14 组件、motion physics）：<https://m3.material.io/blog/building-with-m3-expressive>
- Compose material3 release notes（expressive API 毕业版本）：<https://developer.android.com/jetpack/androidx/releases/compose-material3>
- Android 动态取色：<https://developer.android.com/develop/ui/views/theming/dynamic-colors>
- `MotionDurationScale` / WindowRecomposer 源码：<https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/WindowRecomposer.android.kt>

> **数值来源说明**：本文所有 spring 参数、时长/缓动 token、`ColorScheme` 角色清单、expressive 组件的 opt-in 状态，均以本地 Gradle 缓存中的 `material3-android-1.5.0-alpha25`、`compose-ui 1.12.0-beta01`、`navigation3-ui 1.1.5` 构件反查为准；颜色数值由按 `SeedScheme.kt` 逐行移植的脚本复算（默认种子 `#6ABFCF`），tone 配对与种子无关，8 个预设通用。

---

*生成日期：2026-11-28 · 评审基线 `main` @ `e50fab7` · 只读评审，未修改任何源码*
