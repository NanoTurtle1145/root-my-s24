package cn.nanoturtle.rootmys9280.manager.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import cn.nanoturtle.rootmys9280.manager.R

/**
 * Every destination, as a type.
 *
 * Navigation 3 models the back stack as a plain observable list of these rather than a graph of
 * route strings, so an argument like a module's user id is a constructor parameter and cannot be
 * mis-parsed out of a URL-shaped route.
 */
@Serializable sealed interface Route : NavKey

/**
 * Every panel that exists, and the order a fresh install starts with.
 *
 * Not the order on screen: the reader's own order, and which panels they have hidden, live in
 * SettingsRepository under `nav_panels` and are modelled by NavPanels. What is declared here is the
 * catalogue and the default.
 *
 * Hiding a panel never removes it from this file. The back stack persists NavKeys by class name —
 * NavKeySerializer resolves them with a bare `Class.forName` and has no fallback — and NavDisplay's
 * entryProvider throws for a key it was never given, so a saved stack naming a panel that had been
 * deleted would be a crash rather than a stale tab. Hiding is a fact about the navigation
 * container, and about nothing else.
 */
@Serializable
sealed interface TopLevelRoute : Route {
    @Serializable data object RootFlow : TopLevelRoute

    @Serializable data object Logs : TopLevelRoute

    @Serializable data object Settings : TopLevelRoute

    @Serializable data object About : TopLevelRoute
}

@Serializable data object LogTrace : Route

/** URL shown in the built-in viewer rather than handed to a browser. */
@Serializable data class Web(val url: String) : Route

/** 捐赠页：展示收款二维码。 */
@Serializable data object Donate : Route

/**
 * Label and icon for a bar item. Titles come from resources; no hard-coded English.
 *
 * [key] is the only stable identity this type has, and so the only thing that is ever written to
 * preferences: R8 rewrites class names in a release build, and an ordinal would quietly name a
 * different panel the day a fifth one is declared. See NavPanels for what is stored.
 */
data class TopLevelDestination(
    val key: String,
    val route: TopLevelRoute,
    val icon: ImageVector,
    val labelRes: Int,
)

val TOP_LEVEL_DESTINATIONS: List<TopLevelDestination> =
    listOf(
        TopLevelDestination(
            "rootflow",
            TopLevelRoute.RootFlow,
            Icons.Rounded.Lock,
            R.string.nav_rootflow,
        ),
        TopLevelDestination(
            "logs",
            TopLevelRoute.Logs,
            Icons.AutoMirrored.Rounded.ReceiptLong,
            R.string.nav_logs,
        ),
        TopLevelDestination(
            "settings",
            TopLevelRoute.Settings,
            Icons.Rounded.Settings,
            R.string.nav_settings,
        ),
        TopLevelDestination(
            "about",
            TopLevelRoute.About,
            Icons.Rounded.Info,
            R.string.nav_about,
        ),
    )
