package cn.nanoturtle.rootmys9280.manager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import cn.nanoturtle.rootmys9280.ui.theme.SeedScheme
import cn.nanoturtle.rootmys9280.ui.theme.ThemeMode
import cn.nanoturtle.rootmys9280.ui.theme.toAmoled
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.nanoturtle.rootmys9280.manager.di.ServiceLocator

/** Pixel Material You 风格圆角：大圆角、圆润饱满。 */
private val PixelShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun VectorTheme(content: @Composable () -> Unit) {
    val settings = ServiceLocator.settings
    val modeKey by settings.themeMode.collectAsState()
    val dynamicRequested by settings.dynamicColor.collectAsState()
    val amoled by settings.amoledBlack.collectAsState()
    val seed by settings.seedColor.collectAsState()

    val dark =
        when (ThemeMode.from(modeKey)) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }

    val context = LocalContext.current
    // Dynamic colour is this app's default; the seed below applies before Android 12, or whenever
    // the user would rather choose the colour themselves than inherit their wallpaper's.
    val dynamic = dynamicRequested && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var scheme =
        when {
            dynamic && dark -> dynamicDarkColorScheme(context)
            dynamic -> dynamicLightColorScheme(context)
            else -> remember(seed, dark) { SeedScheme.of(seed, dark) }
        }
    if (dark && amoled) scheme = scheme.toAmoled()

    MaterialExpressiveTheme(
        colorScheme = scheme,
        shapes = PixelShapes,
        // The expressive motion scheme is what makes a state change feel caused rather than
        // scheduled. It drives the status indicator's shape morph and the nav transitions.
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
