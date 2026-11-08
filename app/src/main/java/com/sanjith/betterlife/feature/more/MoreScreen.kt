package com.sanjith.betterlife.feature.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.feature.calm.localTimeText
import com.sanjith.betterlife.feature.events.SwitchRow
import com.sanjith.betterlife.feature.events.TimeButton
import com.sanjith.betterlife.feature.home.DebugTool
import com.sanjith.betterlife.feature.tasks.SectionLabel
import com.sanjith.betterlife.feature.today.PauseRangeSheet
import com.sanjith.betterlife.ui.nav.tabContentPadding
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import com.sanjith.betterlife.domain.time.habitToday
import kotlinx.coroutines.launch

/** One More entry: an icon and a label. */
public data class MoreEntry(val icon: Int, val label: Int, val onOpen: () -> Unit)

/**
 * More (Prompt 17): everything that is not one of the four main tabs. Only what exists so far is
 * listed; Notes, Journal, Body and the rest arrive with their prompts.
 */
@Composable
public fun MoreScreen(entries: List<MoreEntry>, onBusy: () -> Unit, debugTools: List<DebugTool>) {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var pauseSheet by remember { mutableStateOf(false) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            // imePadding: the name field scrolls above the keyboard instead of being covered by it.
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(tabContentPadding()),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(R.string.more_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = Spacing.sm).semantics { heading() },
            )
            val all = entries + listOf(
                MoreEntry(R.drawable.ic_pause_circle, R.string.more_busy, onBusy),
                MoreEntry(R.drawable.ic_spa, R.string.more_pause_habits) { pauseSheet = true },
            )
            all.chunked(COLUMNS).forEach { row ->
                // As tall as its tallest tile, so a two-line label does not make uneven tiles.
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    row.forEach { entry -> Tile(entry, Modifier.weight(1f).fillMaxHeight()) }
                    repeat(COLUMNS - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                }
            }
            settings?.let { s ->
                AlarmSettingsSection(s)
                SettingsGroup(stringResource(R.string.more_your_name)) {
                    NameRow(s.ownerName) { name -> scope.launch { container.settingsRepository.update { it.copy(ownerName = name) } } }
                }
                SettingsGroup(stringResource(R.string.more_nudge)) {
                    SwitchRow(stringResource(R.string.more_nudge_hint), s.habitNudgeEnabled) { on ->
                        scope.launch {
                            container.settingsRepository.update { it.copy(habitNudgeEnabled = on) }
                            container.dailyPrompts.rescheduleAll()
                        }
                    }
                    if (s.habitNudgeEnabled) {
                        TimeButton(localTimeText(s.habitNudgeTime), s.habitNudgeTime, onPick = { t ->
                            t?.let { time ->
                                scope.launch {
                                    container.settingsRepository.update { it.copy(habitNudgeTime = time) }
                                    container.dailyPrompts.rescheduleAll()
                                }
                            }
                        })
                    }
                }
            }
            if (debugTools.isNotEmpty()) {
                SettingsGroup(stringResource(R.string.more_debug)) {
                    debugTools.forEach { tool ->
                        OutlinedButton(onClick = tool.onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)) { Text(stringResource(tool.label)) }
                    }
                }
            }
        }
    }
    if (pauseSheet) {
        var today by remember { mutableStateOf<java.time.LocalDate?>(null) }
        androidx.compose.runtime.LaunchedEffect(Unit) { today = container.clock.habitToday(container.settingsRepository.current().rolloverHour) }
        today?.let { day ->
            PauseRangeSheet(day, onDismiss = { pauseSheet = false }) { start, end, reason ->
                container.appScope.launch {
                    container.pauseRangeRepository.start(start, end, reason)
                    // Reminders due inside the range are held from now on; re-check the schedule.
                    container.reminderEngine.rescheduleAll(com.sanjith.betterlife.reminders.engine.RescheduleTrigger.MANUAL)
                }
            }
        }
    }
}

/** A labelled card of settings on More. */
@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionLabel(title)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm), content = content)
        }
    }
}

/** The owner's name for the Today greeting, until Settings exists (Prompt 27). */
@Composable
private fun NameRow(current: String, onSave: (String) -> Unit) {
    var name by remember(current) { mutableStateOf(current) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.replace("\n", "") },
            singleLine = true,
            label = { Text(stringResource(R.string.onboarding_name_label)) },
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onSave(name.trim()) },
            enabled = name.isNotBlank() && name.trim() != current,
            modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
        ) { Text(stringResource(R.string.editor_save)) }
    }
}

@Composable
private fun Tile(entry: MoreEntry, modifier: Modifier) {
    Card(
        onClick = entry.onOpen,
        modifier = modifier.heightIn(min = Dimens.minTouchTarget),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = Spacing.md, horizontal = Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
        ) {
            Icon(painterResource(entry.icon), null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(entry.label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

private const val COLUMNS = 3
