package cn.nanoturtle.rootmys9280

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.nanoturtle.rootmys9280.ui.theme.AppThemeState
import cn.nanoturtle.rootmys9280.ui.theme.setAccent
import cn.nanoturtle.rootmys9280.ui.theme.setColorSpec
import cn.nanoturtle.rootmys9280.ui.theme.setColorStyle
import cn.nanoturtle.rootmys9280.ui.theme.setDynamicColor
import cn.nanoturtle.rootmys9280.ui.theme.setFloatingBottomBar
import cn.nanoturtle.rootmys9280.ui.theme.setPredictiveBack
import cn.nanoturtle.rootmys9280.ui.theme.setThemeMode
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 主题设置页：照搬 KSU ColorPaletteScreenMiuix（miuix 版本）
 * 裁剪：移除模糊/悬浮导航栏/角标/预测返回/页面缩放（本 App 无此功能）
 */
@Composable
fun ThemeSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // 系统返回键：回上一页（关于页）而不是退出 App
    BackHandler { onBack() }
    val isDark = when (AppThemeState.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val keyColor = AppThemeState.accent
    val monet = AppThemeState.dynamicColor
    var showKeyColorDialog by remember { mutableStateOf(false) }
    var showStyleDialog by remember { mutableStateOf(false) }
    var showSpecDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "主题设置",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回", tint = colorScheme.onBackground)
                    }
                },
            )
        },
        popupHost = { },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .overScrollVertical()
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
        ) {
            item {
                Spacer(Modifier.height(20.dp))
                ThemePreviewCard(
                    keyColor = keyColor,
                    isDark = isDark,
                    monet = monet,
                    style = AppThemeState.colorStyle,
                    spec = AppThemeState.colorSpec,
                )
                Spacer(Modifier.height(28.dp))
            }

            item {
                val themeItems = listOf("跟随系统", "浅色", "深色")
                TabRow(
                    tabs = themeItems,
                    selectedTabIndex = AppThemeState.themeMode.coerceIn(0, 2),
                    onTabSelected = { index -> setThemeMode(context, index) },
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    SwitchPreference(
                        title = "Monet 颜色",
                        summary = "启用 Material You 动态配色",
                        startAction = {
                            Icon(
                                Icons.Rounded.Wallpaper,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        },
                        checked = monet,
                        onCheckedChange = { setDynamicColor(context, it) },
                    )
                    if (monet) {
                        // 关键色（KSU keyColorOptions 同款：默认 + 15 色，对话框选择）
                        top.yukonga.miuix.kmp.preference.ArrowPreference(
                            title = "关键色",
                            summary = accentName(keyColor),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Colorize,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = null,
                                    tint = colorScheme.onBackground,
                                )
                            },
                            onClick = { showKeyColorDialog = true },
                        )
                        if (keyColor != null) {
                            val styles = PaletteStyle.entries
                            top.yukonga.miuix.kmp.preference.ArrowPreference(
                                title = "调色风格",
                                summary = AppThemeState.colorStyle,
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Style,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                onClick = { showStyleDialog = true },
                            )
                            val specs = ColorSpec.SpecVersion.entries
                            top.yukonga.miuix.kmp.preference.ArrowPreference(
                                title = "色域规范",
                                summary = AppThemeState.colorSpec,
                                startAction = {
                                    Icon(
                                        Icons.Rounded.DesignServices,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                onClick = { showSpecDialog = true },
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    SwitchPreference(
                        title = "悬浮导航栏",
                        summary = "iOS 风格悬浮底部导航",
                        startAction = {
                            Icon(
                                Icons.Rounded.CallToAction,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        },
                        checked = AppThemeState.enableFloatingBottomBar,
                        onCheckedChange = { setFloatingBottomBar(context, it) },
                    )
                    SwitchPreference(
                        title = "预测性返回",
                        summary = "Android 14+ 返回手势动画",
                        startAction = {
                            Icon(
                                Icons.AutoMirrored.Rounded.MenuOpen,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        },
                        checked = AppThemeState.enablePredictiveBack,
                        onCheckedChange = { setPredictiveBack(context, it) },
                    )
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // 关键色选择对话框（默认 + 15 预设色）
    if (showKeyColorDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showKeyColorDialog = false },
            title = { androidx.compose.material3.Text("关键色") },
            text = {
                Column {
                    listOf(null to "默认").forEach { (v, n) ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { setAccent(context, v); showKeyColorDialog = false }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = keyColor == v,
                                onClick = { setAccent(context, v); showKeyColorDialog = false },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(n, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                        }
                    }
                    keyColorOptions.forEachIndexed { i, color ->
                        val v: Long? = color
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { setAccent(context, v); showKeyColorDialog = false }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = keyColor == v,
                                onClick = { setAccent(context, v); showKeyColorDialog = false },
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.size(22.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Color(color)),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(keyColorNames[i], style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showKeyColorDialog = false }) { androidx.compose.material3.Text("取消") }
            },
        )
    }
    // 调色风格对话框
    if (showStyleDialog) {
        StyleSpecDialog(
            title = "调色风格",
            items = PaletteStyle.entries.map { it.name },
            current = AppThemeState.colorStyle,
            onSelect = { setColorStyle(context, it); showStyleDialog = false },
            onDismiss = { showStyleDialog = false },
        )
    }
    // 色域规范对话框
    if (showSpecDialog) {
        StyleSpecDialog(
            title = "色域规范",
            items = ColorSpec.SpecVersion.entries.map { it.name },
            current = AppThemeState.colorSpec,
            onSelect = { setColorSpec(context, it); showSpecDialog = false },
            onDismiss = { showSpecDialog = false },
        )
    }
}

/** 手机模型主题预览（照搬 KSU ThemePreviewCardMiuix，简化无悬浮导航栏） */
@Composable
private fun ThemePreviewCard(
    keyColor: Long?,
    isDark: Boolean,
    monet: Boolean,
    style: String,
    spec: String,
) {
    val configuration = LocalConfiguration.current
    val screenRatio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()

    val seedColor = keyColor?.let { Color(it) } ?: colorScheme.primary
    val effectiveStyle = PaletteStyle.entries.firstOrNull { it.name == style } ?: PaletteStyle.TonalSpot
    val effectiveSpec = ColorSpec.SpecVersion.entries.firstOrNull { it.name == spec } ?: ColorSpec.SpecVersion.Default
    val dynamicCs = rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = isDark,
        style = effectiveStyle,
        specVersion = effectiveSpec,
    )

    val bgColor = if (monet) dynamicCs.background else colorScheme.surface
    val textColor = if (monet) dynamicCs.onSurface else colorScheme.onBackground
    val accentCardColor = when {
        monet -> dynamicCs.secondaryContainer
        isDark -> Color(0xFF1A3825)
        else -> Color(0xFFDFFAE4)
    }
    val cardColor = if (monet) dynamicCs.surfaceContainerHighest else colorScheme.surfaceVariant
    val navBarColor = if (monet) dynamicCs.surfaceContainer else colorScheme.surface
    val navSelectedColor = colorScheme.onSurfaceContainer
    val navUnselectedColor = colorScheme.onSurfaceContainer.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .aspectRatio(screenRatio)
                .clip(RoundedCornerShape(20.dp))
                .background(bgColor)
                .border(1.dp, colorScheme.outline, RoundedCornerShape(20.dp)),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "RootMyS9280",
                        fontSize = 12.sp,
                        color = textColor,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(45.dp)
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentCardColor),
                )
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val smallCardHeight = 12.dp
                    val smallCardCount = when {
                        maxHeight >= 96.dp -> 2
                        maxHeight >= 72.dp -> 1
                        else -> 0
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(cardColor),
                        )
                        repeat(smallCardCount) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(smallCardHeight)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(cardColor),
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(textColor.copy(alpha = 0.1f)),
                )
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .fillMaxWidth()
                        .background(navBarColor)
                        .padding(top = 2.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .size(15.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (it == 0) navSelectedColor else navUnselectedColor),
                        )
                    }
                }
            }
        }
    }
}

/** KSU keyColorOptions 同款 15 色 */
private val keyColorOptions: List<Long> = listOf(
    0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5,
    0xFF2196F3, 0xFF00BCD4, 0xFF009688, 0xFF4FAF50, 0xFFFFEB3B,
    0xFFFFC107, 0xFFFF9800, 0xFF795548, 0xFF607D8F, 0xFFFF9CA8,
)

private val keyColorNames = listOf(
    "红色", "粉色", "紫色", "深紫色", "靛蓝",
    "蓝色", "青色", "蓝绿色", "绿色", "黄色",
    "琥珀色", "橙色", "棕色", "蓝灰色", "樱花色",
)

/** 单选列表对话框（调色风格/色域规范通用） */
@Composable
private fun StyleSpecDialog(
    title: String,
    items: List<String>,
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(title) },
        text = {
            Column {
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = current == item,
                            onClick = { onSelect(item) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(item, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("取消") }
        },
    )
}

private fun accentName(accent: Long?): String {
    if (accent == null) return "默认"
    return keyColorNames.getOrNull(keyColorOptions.indexOf(accent)) ?: "自定义"
}
