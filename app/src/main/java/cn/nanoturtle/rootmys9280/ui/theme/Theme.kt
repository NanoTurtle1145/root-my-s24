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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

const val PREFS_NAME = "settings"
const val KEY_THEME_MODE = "theme_mode"
const val KEY_DYNAMIC_COLOR = "dynamic_color"

/** 全局主题状态（单 Activity 应用，直接驱动重组） */
object AppThemeState {
    /** 0=跟随系统 1=浅色 2=深色 */
    var themeMode by mutableIntStateOf(0)
    var dynamicColor by mutableStateOf(true)
}

/** App 启动时从持久化设置恢复主题 */
fun initAppTheme(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    AppThemeState.themeMode = prefs.getInt(KEY_THEME_MODE, 0)
    AppThemeState.dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
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

    val colorScheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        dark -> DarkColorScheme
        else -> LightColorScheme
    }

    // miuix 组件（Switch/Card/NavigationBar 等）使用的颜色：MIUI 默认色系
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
