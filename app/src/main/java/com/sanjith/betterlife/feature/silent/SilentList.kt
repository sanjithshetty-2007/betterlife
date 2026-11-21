package com.sanjith.betterlife.feature.silent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.silent.SilentReminder
import com.sanjith.betterlife.domain.silent.VibrationPattern
import com.sanjith.betterlife.feature.tasks.DueText
import com.sanjith.betterlife.ui.nav.tabContentPadding
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Undo stays offered for about 6 seconds, like Tasks. */
private const val UNDO_WINDOW_MILLIS = 6_000L

/** The Silent tab's content: one card per silent reminder, or a short explanation when there is none. */
@Composable
public fun SilentList(viewModel: SilentListViewModel, snackbar: SnackbarHostState, onOpen: (String) -> Unit) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    SilentUndo(viewModel, snackbar)
    if (reminders.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(tabContentPadding()).padding(Spacing.sm), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.silent_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    val context = LocalContext.current
    val container = (context.applicationContext as BetterLifeApp).container
    val dueText = remember(context) { DueText(context) { container.clock.zone } }
    LazyColumn(contentPadding = tabContentPadding(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(reminders, key = { it.id }) { reminder ->
            SilentCard(
                reminder = reminder,
                times = reminder.times.joinToString(", ") { dueText.time(it) },
                onOpen = { onOpen(reminder.id) },
                onToggle = { viewModel.setEnabled(reminder, it) },
                onDelete = { viewModel.delete(reminder) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun SilentCard(reminder: SilentReminder, times: String, onOpen: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(vertical = Spacing.sm, horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Column(
                modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget).clip(MaterialTheme.shapes.large).clickable(onClick = onOpen).padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(reminder.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(times, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = stringResource(R.string.silent_row_detail, stringResource(patternLabel(reminder.pattern)), stringResource(R.string.silent_seconds, reminder.seconds)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val switchLabel = stringResource(R.string.silent_switch_description, reminder.name)
            Switch(checked = reminder.enabled, onCheckedChange = onToggle, modifier = Modifier.semantics { contentDescription = switchLabel })
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.silent_delete_description, reminder.name), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SilentUndo(viewModel: SilentListViewModel, snackbar: SnackbarHostState) {
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val message = undo?.let { stringResource(R.string.silent_deleted_snack, it.name) }
    val action = stringResource(R.string.undo)
    LaunchedEffect(undo) {
        val offer = undo ?: return@LaunchedEffect
        val expiry = launch {
            delay(UNDO_WINDOW_MILLIS)
            snackbar.currentSnackbarData?.dismiss()
        }
        val result = snackbar.showSnackbar(message.orEmpty(), action, withDismissAction = false, duration = SnackbarDuration.Indefinite)
        expiry.cancel()
        if (result == SnackbarResult.ActionPerformed) viewModel.undo(offer) else viewModel.undoExpired()
    }
}

internal fun patternLabel(pattern: VibrationPattern): Int = when (pattern) {
    VibrationPattern.STEADY -> R.string.silent_pattern_steady
    VibrationPattern.PULSE -> R.string.silent_pattern_pulse
    VibrationPattern.HEARTBEAT -> R.string.silent_pattern_heartbeat
    VibrationPattern.TRIPLE_TAP -> R.string.silent_pattern_triple_tap
    VibrationPattern.WAVE -> R.string.silent_pattern_wave
}
