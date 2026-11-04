package com.sanjith.betterlife.ui.theme

import androidx.compose.ui.graphics.Color

// Design tokens from context.md section 9. Screens never use raw colors: only MaterialTheme or these.
internal object BetterLifeColors {
    val Background = Color(0xFF1A0A0D)
    val Surface = Color(0xFF2A1116)
    val Cherry = Color(0xFF7A1F2B)
    val AccentYellow = Color(0xFFE0A800)
    val Text = Color(0xFFF5E9E0)

    /** Text at reduced emphasis, for secondary lines. */
    val TextMuted = Text.copy(alpha = 0.72f)

    /** The soft 1 dp border drawn around cards. */
    val SoftBorder = Text.copy(alpha = 0.12f)

    // Traffic-light status colors, kept warm to fit the palette. Always paired with an icon or word.
    val StatusGood = Color(0xFF7BC67E)
    val StatusWarn = AccentYellow
    val StatusBad = Color(0xFFFF6B5E)

    /** Dims whatever is behind the reminder pop-up. */
    val Scrim = Background.copy(alpha = 0.82f)
}
