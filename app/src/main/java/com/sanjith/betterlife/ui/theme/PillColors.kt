package com.sanjith.betterlife.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.Composable

/**
 * The selected pill of a segmented row (Calendar views, event type, task priority and level) is the
 * brand's cherry, as in docs/design (owner, 2026-11-15). Only these rows: selected filter chips keep
 * Material's own colour, so the theme's secondaryContainer is deliberately left unset.
 */
@Composable
public fun pillColors(): SegmentedButtonColors = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.secondary,
    activeContentColor = MaterialTheme.colorScheme.onSecondary,
    activeBorderColor = MaterialTheme.colorScheme.outline,
)
