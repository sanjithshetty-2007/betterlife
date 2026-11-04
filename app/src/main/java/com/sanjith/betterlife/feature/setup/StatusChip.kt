package com.sanjith.betterlife.feature.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.setup.SetupLevel
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing

/** Green / yellow / red chip with a symbol and a word, so the status never depends on color alone. */
@Composable
internal fun StatusChip(level: SetupLevel, modifier: Modifier = Modifier) {
    val color = level.color()
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = color.copy(alpha = CHIP_FILL_ALPHA),
        border = BorderStroke(Dimens.borderWidth, color),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(text = stringResource(level.symbol()), color = color, style = MaterialTheme.typography.labelLarge)
            Text(text = stringResource(level.label()), color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private const val CHIP_FILL_ALPHA = 0.16f

private fun SetupLevel.color(): Color = when (this) {
    SetupLevel.GREEN -> BetterLifeColors.StatusGood
    SetupLevel.YELLOW -> BetterLifeColors.StatusWarn
    SetupLevel.RED -> BetterLifeColors.StatusBad
}

private fun SetupLevel.symbol(): Int = when (this) {
    SetupLevel.GREEN -> R.string.setup_symbol_green
    SetupLevel.YELLOW -> R.string.setup_symbol_yellow
    SetupLevel.RED -> R.string.setup_symbol_red
}
