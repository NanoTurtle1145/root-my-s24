package cn.nanoturtle.rootmys9280.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

const val PREFS_NAME = "settings"
const val KEY_THEME_MODE = "theme_mode"
const val KEY_DYNAMIC_COLOR = "dynamic_color"
const val KEY_ACCENT = "accent_color"
const val KEY_COLOR_STYLE = "color_style"
const val KEY_COLOR_SPEC = "color_spec"
const val KEY_FLOATING_BAR = "floating_bottom_bar"
const val KEY_PREDICTIVE_BACK = "predictive_back"

/** 主题强调色预设（seed） */
val AccentPresets = listOf(
    0xFF00BCD4 to "青色",
    0xFF2196F3 to "蓝色",
    0xFF7C4DFF to "紫色",
    0xFF4CAF50 to "绿色",
    0xFFFF9800 to "橙色",
    0xFFF44336 to "红色",
)

/** 全局主题状态（单 Activity 应用，直接驱动重组） */
object AppThemeState {
    /** 0=跟随系统 1=浅色 2=深色 */
    var themeMode by mutableIntStateOf(0)
    var dynamicColor by mutableStateOf(true)

    /** 强调色 seed（null=跟随系统动态色） */
    var accent: Long? by mutableStateOf(null)

    /** 调色风格（materialkolor PaletteStyle 名）与色域规范 */
    var colorStyle by mutableStateOf("TonalSpot")
    var colorSpec by mutableStateOf("SPEC_2021")

    /** 悬浮底部导航栏 */
    var enableFloatingBottomBar by mutableStateOf(false)

    /** 预测性返回动画 */
    var enablePredictiveBack by mutableStateOf(true)
}

/** App 启动时从持久化设置恢复主题 */
fun initAppTheme(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    AppThemeState.themeMode = prefs.getInt(KEY_THEME_MODE, 0)
    AppThemeState.dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
    AppThemeState.colorStyle = prefs.getString(KEY_COLOR_STYLE, "TonalSpot") ?: "TonalSpot"
    AppThemeState.colorSpec = prefs.getString(KEY_COLOR_SPEC, "SPEC_2021") ?: "SPEC_2021"
    AppThemeState.enableFloatingBottomBar = prefs.getBoolean(KEY_FLOATING_BAR, false)
    AppThemeState.enablePredictiveBack = prefs.getBoolean(KEY_PREDICTIVE_BACK, true)
    if (prefs.contains(KEY_ACCENT)) {
        AppThemeState.accent = prefs.getLong(KEY_ACCENT, 0)
    }
}

fun setThemeMode(context: Context, mode: Int) {
    AppThemeState.themeMode = mode
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putInt(KEY_THEME_MODE, mode).apply()
}

fun setDynamicColor(context: Context, enabled: Boolean) {
    AppThemeState.dynamicColor = enabled
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
}

fun setAccent(context: Context, accent: Long?) {
    AppThemeState.accent = accent
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().apply {
            if (accent == null) remove(KEY_ACCENT) else putLong(KEY_ACCENT, accent)
        }.apply()
}

fun setColorStyle(context: Context, style: String) {
    AppThemeState.colorStyle = style
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_COLOR_STYLE, style).apply()
}

fun setColorSpec(context: Context, spec: String) {
    AppThemeState.colorSpec = spec
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_COLOR_SPEC, spec).apply()
}

fun setFloatingBottomBar(context: Context, enabled: Boolean) {
    AppThemeState.enableFloatingBottomBar = enabled
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_FLOATING_BAR, enabled).apply()
}

fun setPredictiveBack(context: Context, enabled: Boolean) {
    AppThemeState.enablePredictiveBack = enabled
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_PREDICTIVE_BACK, enabled).apply()
}

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/** Miuix / MIUI 风格：大圆角卡片与列表容器 */
private val MiuixShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun RootMyS9280Theme(
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (AppThemeState.themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }
    val dynamic = AppThemeState.dynamicColor
    val accent = AppThemeState.accent
    val useDynamic = dynamic

    val colorScheme = when {
        accent != null -> {
            val seed = Color(accent)
            if (dark) {
                darkColorScheme(primary = seed, secondary = seed, tertiary = seed)
            } else {
                lightColorScheme(primary = seed, secondary = seed, tertiary = seed)
            }
        }

        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        dark -> DarkColorScheme
        else -> LightColorScheme
    }

    // miuix 组件配色：MIUI 默认青绿色系（miuix Colors 不可从外部改强调色）
    val miuixColors = if (dark) miuixDarkColorScheme() else miuixLightColorScheme()

    MiuixTheme(colors = miuixColors, textStyles = defaultTextStyles()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = MiuixShapes,
            content = content
        )
    }
}
