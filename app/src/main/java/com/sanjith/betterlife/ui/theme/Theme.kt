package com.sanjith.betterlife.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Dark AMOLED is the only scheme for now (context.md section 9).
private val DarkColors = darkColorScheme(
    primary = BetterLifeColors.AccentYellow,
    onPrimary = BetterLifeColors.Background,
    secondary = BetterLifeColors.Cherry,
    onSecondary = BetterLifeColors.Text,
    background = BetterLifeColors.Background,
    onBackground = BetterLifeColors.Text,
    surface = BetterLifeColors.Surface,
    onSurface = BetterLifeColors.Text,
    surfaceVariant = BetterLifeColors.Surface,
    onSurfaceVariant = BetterLifeColors.TextMuted,
    outline = BetterLifeColors.SoftBorder,
    outlineVariant = BetterLifeColors.SoftBorder,
    inverseSurface = BetterLifeColors.Text,
    inverseOnSurface = BetterLifeColors.Background,
    inversePrimary = BetterLifeColors.Cherry,
)

@Composable
public fun BetterLifeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        shapes = BetterLifeShapes,
        typography = Typography(),
        content = content,
    )
}
