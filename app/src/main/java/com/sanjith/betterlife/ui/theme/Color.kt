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

    /**
     * Swatches for the owner's event colour categories (Prompt 10), picked by index. Bright enough to
     * read as a dot or stripe on the dark background; the category name is always shown with it.
     */
    val CategoryPalette: List<Color> = listOf(
        Color(0xFFFF6B5E),
        Color(0xFFE0A800),
        Color(0xFF7BC67E),
        Color(0xFF5EC4D6),
        Color(0xFF7E8CFF),
        Color(0xFFC77DFF),
        Color(0xFFFF8FB8),
        Color(0xFFB0A49C),
    )

    /** Soft cherry tint behind row icons (the design's "soft tint", added in Prompt 11). */
    val CherryTint = Cherry.copy(alpha = 0.55f)

    /** Habits (Prompt 15): greyed boxes (no such day, before the start), and the heatmap's empty day. */
    val Greyed = Text.copy(alpha = 0.06f)

    /** A habit colour at heatmap strength: a kept or paused day is drawn lighter than a done day. */
    const val HEATMAP_SOFT_ALPHA = 0.45f

    /** The border of a habit box that can still be ticked; future boxes use the faint SoftBorder. */
    val BoxOpenBorder = Text.copy(alpha = 0.32f)

    /** Blip (Prompt 16): the soft light sheen on the body and bulb, rosy cheeks, and the thin outline of the arms. */
    val MascotSheen = Text.copy(alpha = 0.16f)
    val MascotBlush = StatusBad.copy(alpha = 0.45f)
    val MascotShade = Background.copy(alpha = 0.35f)

    /** The antenna bulb when Blip is sleepy or down: the mood light turned low. */
    val BulbDim = AccentYellow.copy(alpha = 0.45f)

    /** Dims whatever is behind the reminder pop-up. */
    val Scrim = Background.copy(alpha = 0.82f)
}
