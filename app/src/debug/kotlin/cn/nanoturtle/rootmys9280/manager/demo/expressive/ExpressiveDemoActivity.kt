package cn.nanoturtle.rootmys9280.manager.demo.expressive

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cn.nanoturtle.rootmys9280.ui.theme.SeedScheme

/**
 * A demo of Material 3 Expressive as the design language, rather than as a theme wrapper.
 *
 * Everything on the screen is built from what `material3 1.5.0-alpha25` actually ships: the
 * expressive motion scheme (spring physics, spatial and effects as separate channels), the 35-shape
 * library with `Morph`, emphasized type, and the promoted expressive components (`ButtonGroup`,
 * `SplitButton`, `HorizontalFloatingToolbar`) plus the ones still behind
 * `ExperimentalMaterial3ExpressiveApi` (`LoadingIndicator`, `ContainedLoadingIndicator`,
 * `MaterialShapes`). No hardcoded durations and no hardcoded spring numbers: every animation asks
 * `MaterialTheme.motionScheme` for its spec, so the screen keeps working if the scheme is switched
 * to `standard()`.
 *
 * It is a debug-only activity (see `src/debug/AndroidManifest.xml`), like the state demos next to
 * it: a release build never compiles this class.
 */
class ExpressiveDemoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // `null` follows the system; `true`/`false` is a local override so the theme card can
            // demonstrate what the colour scheme does in the other brightness without touching the
            // device setting.
            var override by remember { mutableStateOf<Boolean?>(null) }
            val dark = override ?: isSystemInDarkTheme()

            ExpressiveDemoTheme(dark) {
                ExpressiveDemoScreen(
                    darkPreference = override,
                    onDarkPreference = { override = it },
                )
            }
        }
    }
}

/**
 * The theme half of the demo, and deliberately the same arrangement the app should be able to use:
 *
 * - dynamic colour from the wallpaper when the platform has it, the app's own LCh seed otherwise
 *   (so the demo runs on API 27 through 36 without a different palette);
 * - the fixed accent roles filled in, which `SeedScheme` leaves at their baseline values because
 *   they were added to `ColorScheme` after it was written — those are the roles an expressive
 *   "brand block" wants, and they are the ones a generator forgets;
 * - all eight shape tokens, including the three the app's `PixelShapes` leaves at Material's
 *   defaults (`largeIncreased`, `extraLargeIncreased`, `extraExtraLarge`), so expressive components
 *   agree with the cards around them;
 * - system bar icons follow this screen's resolved brightness, not the system's.
 */
@Composable
fun ExpressiveDemoTheme(dark: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = remember(dark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            SeedScheme.of(SeedScheme.DEFAULT_SEED, dark).withFixedRoles()
        }
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        shapes = DemoShapes,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/** All eight tokens, on the app's 8 / 12 / 20 / 28 / 32 scale with the three gaps filled in. */
private val DemoShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        largeIncreased = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
        extraLargeIncreased = RoundedCornerShape(36.dp),
        extraExtraLarge = RoundedCornerShape(48.dp),
    )

/**
 * Fills the twelve `*Fixed*` roles a seed-generated scheme would otherwise leave at Material's
 * baseline purple. Fixed roles keep the same tone in light and dark on purpose: they are the
 * accent that does not move when the theme does.
 */
private fun ColorScheme.withFixedRoles(): ColorScheme {
    val (chroma, hue) = with(SeedScheme) { Color(SeedScheme.DEFAULT_SEED).toWheel() }
    val primary = SeedScheme.Ramp(hue, chroma.coerceAtLeast(48f))
    val secondary = SeedScheme.Ramp(hue, 16f)
    val tertiary = SeedScheme.Ramp(hue + 60f, 24f)
    return copy(
        primaryFixed = primary[90],
        primaryFixedDim = primary[80],
        onPrimaryFixed = primary[10],
        onPrimaryFixedVariant = primary[30],
        secondaryFixed = secondary[90],
        secondaryFixedDim = secondary[80],
        onSecondaryFixed = secondary[10],
        onSecondaryFixedVariant = secondary[30],
        tertiaryFixed = tertiary[90],
        tertiaryFixedDim = tertiary[80],
        onTertiaryFixed = tertiary[10],
        onTertiaryFixedVariant = tertiary[30],
    )
}
