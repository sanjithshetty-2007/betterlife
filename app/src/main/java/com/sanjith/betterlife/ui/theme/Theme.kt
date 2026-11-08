package com.sanjith.betterlife.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

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

// docs/design: bold headlines and numbers over quiet body text, on the system font.
private val BetterLifeTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
public fun BetterLifeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        shapes = BetterLifeShapes,
        typography = BetterLifeTypography,
        content = content,
    )
}
