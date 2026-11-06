package com.sanjith.betterlife.feature.setup

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.setup.ClosedAppTestResult
import com.sanjith.betterlife.domain.setup.HealthSummary
import com.sanjith.betterlife.domain.setup.SetupLevel
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Date

/**
 * Reminder Health (Prompt 11; the Setup Checklist before): a score, Fix all, the two test reminders,
 * the Android rows with a Fix button each, and the Vivo rows the owner ticks by hand.
 */
@Composable
public fun SetupScreen(
    onBack: () -> Unit,
    onOpenCenter: () -> Unit = {},
    viewModel: SetupViewModel = viewModel(factory = SetupViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prompt by viewModel.fixAllPrompt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val quickTitle = stringResource(R.string.health_test_quick_title)
    val quickStarted = stringResource(R.string.health_test_quick_started)
    val closedTitle = stringResource(R.string.health_test_closed_title)

    // Re-check on every resume: the owner usually returns here from a system settings page.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }) { innerPadding ->
        val current = state as? SetupUiState.Loaded
        if (current == null) {
            LoadingState(Modifier.padding(innerPadding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.md,
                end = Spacing.md,
                top = innerPadding.calculateTopPadding() + Spacing.sm,
                bottom = innerPadding.calculateBottomPadding() + Spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { HealthHeader(onBack, onRecheck = viewModel::refresh) }
            item {
                HeroCard(current.summary, current.lastCheckedAt, current.now) {
                    viewModel.startFixAll()?.let { SystemSettingsLauncher.open(context, it) }
                }
            }
            item {
                TestButtons(
                    onQuick = {
                        viewModel.testQuick(quickTitle)
                        scope.launch { snackbar.showSnackbar(quickStarted) }
                    },
                    onClosed = { viewModel.testAfterClosing(closedTitle) },
                )
            }
            current.closedAppTest?.let { result -> item { ClosedAppTestCard(result) } }
            val (manual, detected) = current.status.items.partition { it.check.isManual }
            item { SectionLabel(stringResource(R.string.health_section_android)) }
            item { RowsCard(detected) { SystemSettingsLauncher.open(context, it.check) } }
            if (manual.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.health_section_vivo)) }
                item {
                    VivoCard(
                        manual,
                        onOpen = { SystemSettingsLauncher.open(context, it.check) },
                        onTick = { item, ticked -> viewModel.setManualTick(item.check, ticked) },
                    )
                }
            }
            item {
                TextButton(onClick = onOpenCenter, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                    Text(stringResource(R.string.health_open_center))
                }
            }
        }
    }
    prompt?.let { p ->
        FixAllDialog(
            prompt = p,
            onOpen = { check -> viewModel.visit(check); SystemSettingsLauncher.open(context, check) },
            onStop = viewModel::stopFixAll,
        )
    }
}

@Composable
private fun HealthHeader(onBack: () -> Unit, onRecheck: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.setup_back), tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRecheck) {
            Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.health_recheck), tint = MaterialTheme.colorScheme.onBackground)
        }
    }
}

/** Score ring (animates to the new score after a re-check), summary line, last check, Fix all. */
@Composable
private fun HeroCard(summary: HealthSummary, lastCheckedAt: Instant?, now: Instant, onFixAll: () -> Unit) {
    val worst = when {
        summary.redChecks.isNotEmpty() -> SetupLevel.RED
        summary.needsYou.isNotEmpty() -> SetupLevel.YELLOW
        else -> SetupLevel.GREEN
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, worst.statusColor().copy(alpha = HERO_BORDER_ALPHA)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ScoreRing(summary.score, summary.total, worst)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.weight(1f)) {
                Text(
                    text = if (summary.needsYou.isEmpty()) stringResource(R.string.health_all_set)
                    else pluralStringResource(R.plurals.health_needs_you, summary.needsYou.size, summary.needsYou.size),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(checkedText(lastCheckedAt, now), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (summary.redChecks.isNotEmpty()) {
                    Button(onClick = onFixAll, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                        Icon(painterResource(R.drawable.ic_build), contentDescription = null, modifier = Modifier.size(Dimens.smallIconSize))
                        Text(stringResource(R.string.health_fix_all), modifier = Modifier.padding(start = Spacing.sm))
                    }
                }
            }
        }
    }
}

private const val HERO_BORDER_ALPHA = 0.5f

@Composable
private fun ScoreRing(score: Int, total: Int, level: SetupLevel) {
    val progress by animateFloatAsState(if (total == 0) 0f else score.toFloat() / total, label = "score")
    val description = stringResource(R.string.health_score_description, score, total)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(Dimens.healthRing).semantics { contentDescription = description }) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(Dimens.healthRing),
            color = level.statusColor(),
            trackColor = MaterialTheme.colorScheme.outline,
            strokeWidth = Dimens.healthRingStroke,
        )
        Text(stringResource(R.string.health_score, score, total), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun checkedText(lastCheckedAt: Instant?, now: Instant): String {
    val at = lastCheckedAt ?: return stringResource(R.string.health_checked_just_now)
    val minutes = Duration.between(at, now).toMinutes()
    return when {
        minutes < 1 -> stringResource(R.string.health_checked_just_now)
        minutes < MINUTES_SHOWN -> pluralStringResource(R.plurals.health_checked_minutes, minutes.toInt(), minutes.toInt())
        else -> stringResource(R.string.health_checked_at, timeText(at))
    }
}

private const val MINUTES_SHOWN = 60

@Composable
private fun timeText(at: Instant): String =
    DateFormat.getTimeFormat(LocalContext.current).format(Date.from(at.atZone(ZoneId.systemDefault()).toInstant()))

@Composable
private fun TestButtons(onQuick: () -> Unit, onClosed: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Button(
            onClick = onQuick,
            modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
        ) {
            Icon(painterResource(R.drawable.ic_timer), contentDescription = null, modifier = Modifier.size(Dimens.smallIconSize))
            // Labels may wrap: large font sizes must never clip them (agent.md section 6).
            Text(stringResource(R.string.health_test_quick), modifier = Modifier.padding(start = Spacing.sm), textAlign = TextAlign.Center)
        }
        OutlinedButton(onClick = onClosed, modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget)) {
            Icon(painterResource(R.drawable.ic_phone), contentDescription = null, modifier = Modifier.size(Dimens.smallIconSize))
            Text(stringResource(R.string.health_test_closed), modifier = Modifier.padding(start = Spacing.sm), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ClosedAppTestCard(result: ClosedAppTestResult) {
    val (text, color, icon) = when (result) {
        is ClosedAppTestResult.Waiting ->
            Triple(stringResource(R.string.health_test_closed_waiting, timeText(result.due)), BetterLifeColors.StatusWarn, R.drawable.ic_schedule)
        is ClosedAppTestResult.Fired -> Triple(
            stringResource(R.string.health_test_closed_fired, timeText(result.at), result.lateBy.seconds.toInt()),
            BetterLifeColors.StatusGood,
            R.drawable.ic_check_circle,
        )
        is ClosedAppTestResult.DidNotFire ->
            Triple(stringResource(R.string.health_test_closed_failed, timeText(result.due)), BetterLifeColors.StatusBad, R.drawable.ic_error)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(Dimens.borderWidth, color),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(Dimens.iconSize))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.sm, top = Spacing.sm),
    )
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.setup_loading),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = Spacing.md),
        )
    }
}
