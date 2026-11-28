package cn.nanoturtle.rootmys9280.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Collapses every background role to true black, for OLED panels.
 *
 * Applied on top of whichever dark scheme is in use — including a dynamic one — so the accent
 * colours the user's wallpaper produced survive.
 *
 * Which palette is in force is never the app's choice: from API 31 the wallpaper decides it, and
 * below that, or whenever the user turns dynamic colour off, the seed they picked does. So nothing
 * may rely on a specific hue to carry meaning — the status header and version mark carry state by
 * shape, icon, label and motion as well as by colour.
 */
fun ColorScheme.toAmoled(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainer = Color(0xFF101010),
        surfaceContainerHigh = Color(0xFF161616),
        surfaceContainerHighest = Color(0xFF1C1C1C),
        // The three roles the first pass missed. `surfaceBright` is the "brightest surface" role
        // and would otherwise stay at tone 24 — a grey block on true black. `surfaceVariant` is
        // still what Markdown code blocks and log washes use, and at its dark tone it reads as a
        // light grey patch. `outlineVariant` draws dividers; at 1.8:1 against the container it was
        // a divider nobody could see, so it moves up with the surface ladder rather than staying
        // at its un-pressed tone.
        surfaceBright = Color(0xFF1C1C1C),
        surfaceVariant = Color(0xFF1A1A1A),
        outlineVariant = Color(0xFF2E2E2E),
    )
