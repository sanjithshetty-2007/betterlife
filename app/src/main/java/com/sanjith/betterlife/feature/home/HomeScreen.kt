package com.sanjith.betterlife.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.sanjith.betterlife.R
import com.sanjith.betterlife.ui.theme.BetterLifeTheme
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.launch

/** A debug-only shortcut shown on the home screen. */
public data class DebugTool(@StringRes val label: Int, val onOpen: () -> Unit)

/**
 * Placeholder home screen proving the theme: title, a bordered card, a yellow button and the checklist link.
 * [debugTools] is empty in release builds, which hides every debug entry.
 */
@Composable
public fun HomeScreen(
    onOpenTasks: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenAgenda: () -> Unit = {},
    onOpenHabits: () -> Unit = {},
    onOpenCenter: () -> Unit = {},
    /** Titles of the RED Reminder Health rows; the banner shows when this is not empty. */
    healthProblems: List<String> = emptyList(),
    /** Shown above the welcome card while a pause is on (Prompt 12). */
    pauseBanner: (@Composable () -> Unit)? = null,
    onPause: () -> Unit = {},
    onOpenQuiet: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
    /** The small sync status line (Prompt S2); null in previews. */
    syncIndicator: (@Composable () -> Unit)? = null,
    debugTools: List<DebugTool> = emptyList(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snackbarText = stringResource(R.string.home_snackbar)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (healthProblems.isNotEmpty()) HealthBanner(healthProblems, onOpenSetup)
            syncIndicator?.invoke()
            pauseBanner?.invoke()
            WelcomeCard()
            Button(
                onClick = { scope.launch { snackbarHostState.showSnackbar(snackbarText) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.home_button))
            }
            Button(
                onClick = onOpenTasks,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.tasks_title))
            }
            Button(
                onClick = onOpenAgenda,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.agenda_title))
            }
            Button(
                onClick = onOpenHabits,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.habits_title))
            }
            OutlinedButton(
                onClick = onOpenSetup,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.setup_home_button))
            }
            OutlinedButton(
                onClick = onOpenCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.center_home_button))
            }
            OutlinedButton(
                onClick = onPause,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.pause_home_button))
            }
            OutlinedButton(
                onClick = onOpenQuiet,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.quiet_home_button))
            }
            OutlinedButton(
                onClick = onOpenBackup,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.backup_home_button))
            }
            OutlinedButton(
                onClick = onOpenAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.account_home_button))
            }
            debugTools.forEach { tool ->
                OutlinedButton(
                    onClick = tool.onOpen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.minTouchTarget),
                ) {
                    Text(text = stringResource(tool.label))
                }
            }
        }
    }
}

/** Red banner (icon + words, not colour alone) when a Reminder Health row is RED (Prompt 11). */
@Composable
private fun HealthBanner(problems: List<String>, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
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

@Composable
private fun WelcomeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.home_card_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.home_card_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    BetterLifeTheme { HomeScreen(onOpenTasks = {}, onOpenSetup = {}) }
}
