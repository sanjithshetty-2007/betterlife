package com.sanjith.betterlife.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.sanjith.betterlife.R
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing

// The placeholder Home screen was replaced by Today (Prompt 17). What it had that is still used lives here.

/** A debug-only shortcut, listed on More in debug builds. */
public data class DebugTool(@StringRes val label: Int, val onOpen: () -> Unit)

/** Red banner (icon + words, not colour alone) when a Reminder Health row is RED (Prompt 11). Shown on Today. */
@Composable
public fun HealthBanner(problems: List<String>, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(Dimens.borderWidth, BetterLifeColors.StatusBad),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = BetterLifeColors.StatusBad, modifier = Modifier.size(Dimens.iconSize))
            Text(
                text = stringResource(R.string.home_health_banner, problems.joinToString(", ")),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
