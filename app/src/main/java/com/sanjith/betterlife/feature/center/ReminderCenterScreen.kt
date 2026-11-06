package com.sanjith.betterlife.feature.center

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.R
import com.sanjith.betterlife.data.db.dao.CenterLogRow
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.model.ReminderLogStatus
import com.sanjith.betterlife.domain.reminder.CenterFilter
import com.sanjith.betterlife.domain.reminder.ReminderLogRules
import com.sanjith.betterlife.feature.setup.StatusPill
import com.sanjith.betterlife.feature.tasks.levelLabel
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

/** The Reminder Center (Prompt 11): every reminder event of the last 60 days, with the reason. */
@Composable
public fun ReminderCenterScreen(
    onBack: () -> Unit,
    onOpenItem: (ReminderItemType, String) -> Unit,
    viewModel: ReminderCenterViewModel = viewModel(factory = ReminderCenterViewModel.Factory),
) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedText = stringResource(R.string.center_item_deleted)

    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Header(onBack)
            FilterRow(filter) { viewModel.filter.value = it }
            val list = days ?: return@Column
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.center_empty), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                return@Column
            }
            LazyColumn(contentPadding = PaddingValues(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                list.forEach { day ->
                    item(key = "day-${day.date}") { DayLabel(day.date, viewModel.today()) }
                    item(key = "card-${day.date}") {
                        DayCard(day.rows, viewModel.zone) { row ->
                            val type = row.itemType
                            val id = row.itemId
                            if (type == null || id == null || row.itemDeletedAt != null) {
                                scope.launch { snackbar.showSnackbar(deletedText) }
                            } else {
                                onOpenItem(type, id)
                            }
                        }
                    }
                }
                item(key = "kept") {
                    Text(
                        stringResource(R.string.center_kept),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.setup_back), tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(stringResource(R.string.center_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun FilterRow(selected: CenterFilter, onPick: (CenterFilter) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(CenterFilter.entries) { f ->
            FilterChip(selected = f == selected, onClick = { onPick(f) }, label = { Text(stringResource(f.label())) })
        }
    }
}

@Composable
private fun DayLabel(date: LocalDate, today: LocalDate) {
    val text = when (date) {
        today -> stringResource(R.string.due_today)
        today.minusDays(1) -> stringResource(R.string.due_yesterday)
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.sm, top = Spacing.sm),
    )
}

@Composable
private fun DayCard(rows: List<CenterLogRow>, zone: ZoneId, onOpen: (CenterLogRow) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            CenterRow(row, zone) { onOpen(row) }
        }
    }
}

/** Time, item, level, the reason sentence (when there is one) and the status as icon + word. */
@Composable
private fun CenterRow(row: CenterLogRow, zone: ZoneId, onClick: () -> Unit) {
    val log = row.log
    val look = log.status.look()
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            timeText(log.firedAt, zone),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(Dimens.centerTimeColumn),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                row.itemTitle ?: stringResource(R.string.center_item_gone),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(levelLabel(log.level)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            reasonLine(log.status, log.reasonCode)?.let {
                Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        val count = if (log.status == ReminderLogStatus.SNOOZED) ReminderLogRules.snoozeCount(log.detail) else null
        StatusPill(
            icon = look.icon,
            text = count?.let { stringResource(R.string.center_snoozed_count, it) } ?: stringResource(look.label),
            color = look.tone.color(),
        )
    }
}

@Composable
private fun Tone.color(): Color = when (this) {
    Tone.GOOD -> BetterLifeColors.StatusGood
    Tone.WARN -> BetterLifeColors.StatusWarn
    Tone.BAD -> BetterLifeColors.StatusBad
    Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun timeText(millis: Long, zone: ZoneId): String {
    val local = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
    return DateFormat.getTimeFormat(LocalContext.current).format(Date.from(local.atZone(ZoneId.systemDefault()).toInstant()))
}
