package com.sanjith.betterlife.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.sanjith.betterlife.R
import com.sanjith.betterlife.data.db.entity.PauseRangeEntity
import com.sanjith.betterlife.domain.habit.PauseReason
import com.sanjith.betterlife.feature.events.DateButton
import com.sanjith.betterlife.feature.events.SwitchRow
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Pause / vacation / sick mode for habits (Prompt 17): a date range, a reason, start. Written like the "I'm busy" sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PauseRangeSheet(today: LocalDate, onDismiss: () -> Unit, onStart: (LocalDate, LocalDate?, PauseReason) -> Unit) {
    var start by remember { mutableStateOf(today) }
    var end by remember { mutableStateOf<LocalDate?>(today.plusDays(DEFAULT_DAYS)) }
    var reason by remember { mutableStateOf(PauseReason.VACATION) }
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(Dimens.iconTile).background(BetterLifeColors.CherryTint, MaterialTheme.shapes.small),
                ) { Icon(painterResource(R.drawable.ic_pause_circle), null, tint = MaterialTheme.colorScheme.primary) }
                Column {
                    Text(stringResource(R.string.pause_range_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.pause_range_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PauseReason.entries.forEach { r ->
                    FilterChip(selected = r == reason, onClick = { reason = r }, label = { Text(stringResource(reasonLabel(r))) })
                }
            }
            DateButton(stringResource(R.string.pause_range_from, start.format(format)), start) { picked ->
                start = picked
                end?.let { if (it.isBefore(picked)) end = picked }
            }
            SwitchRow(stringResource(R.string.pause_range_open_ended), end == null) { open -> end = if (open) null else start.plusDays(DEFAULT_DAYS) }
            end?.let { last ->
                DateButton(stringResource(R.string.pause_range_until, last.format(format)), last) { picked -> end = if (picked.isBefore(start)) start else picked }
            }
            Button(
                onClick = {
                    onStart(start, end, reason)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(R.string.pause_range_start)) }
        }
    }
}

/** "Vacation: habits paused until 14 Nov" with End pause; ending delivers any held reminders at once. */
@Composable
public fun PauseRangeBanner(range: PauseRangeEntity, onEnd: () -> Unit) {
    val reason = stringResource(reasonLabel(range.reason))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.primary),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(painterResource(R.drawable.ic_pause_circle), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Dimens.iconSize))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    range.endDate?.let { stringResource(R.string.pause_range_banner, reason, it.format(DateTimeFormatter.ofPattern("d MMM"))) }
                        ?: stringResource(R.string.pause_range_banner_open, reason),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(stringResource(R.string.pause_range_banner_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onEnd, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text(stringResource(R.string.pause_range_end)) }
        }
    }
}

private const val DEFAULT_DAYS = 6L

internal fun reasonLabel(reason: PauseReason): Int = when (reason) {
    PauseReason.PAUSE -> R.string.pause_range_reason_pause
    PauseReason.VACATION -> R.string.pause_range_reason_vacation
    PauseReason.SICK -> R.string.pause_range_reason_sick
}
