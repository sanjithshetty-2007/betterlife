package com.sanjith.betterlife.feature.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.setup.SetupCheck
import com.sanjith.betterlife.domain.setup.SetupItem
import com.sanjith.betterlife.domain.setup.SetupLevel
import com.sanjith.betterlife.domain.setup.SetupReason
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing

// Reminder Health's grouped rows and the Fix all dialog (split from SetupScreen.kt to keep files small).

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) { Column { content() } }
}

@Composable
internal fun RowsCard(items: List<SetupItem>, onFix: (SetupItem) -> Unit) {
    GroupCard {
        items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            HealthRow(item) { onFix(item) }
        }
    }
}

/** Icon tile, title, what is wrong (when not green), and either a status pill or a Fix button. */
@Composable
private fun HealthRow(item: SetupItem, onFix: () -> Unit) {
    val text = item.check.rowText()
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        IconTile(text.icon)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(text.title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (item.level != SetupLevel.GREEN) {
                Text(stringResource(item.reason.text()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(text.why), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                text.howTo?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        val title = stringResource(text.title)
        val fixDescription = stringResource(R.string.health_open_settings_for, title)
        when (item.level) {
            SetupLevel.GREEN -> StatusPill(R.drawable.ic_check_circle, stringResource(R.string.setup_level_green), BetterLifeColors.StatusGood)
            SetupLevel.RED -> Button(onClick = onFix, modifier = Modifier.heightIn(min = Dimens.minTouchTarget).semantics { contentDescription = fixDescription }) {
                Text(stringResource(R.string.health_fix))
            }
            SetupLevel.YELLOW -> OutlinedButton(onClick = onFix, modifier = Modifier.heightIn(min = Dimens.minTouchTarget).semantics { contentDescription = fixDescription }) {
                Text(stringResource(R.string.health_fix))
            }
        }
    }
}

@Composable
private fun IconTile(icon: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(Dimens.iconTile).background(BetterLifeColors.CherryTint, MaterialTheme.shapes.small),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Dimens.iconSize))
    }
}

@Composable
internal fun VivoCard(items: List<SetupItem>, onOpen: (SetupItem) -> Unit, onTick: (SetupItem, Boolean) -> Unit) {
    GroupCard {
        items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            VivoRow(item, onOpen = { onOpen(item) }, onTick = { onTick(item, it) })
        }
    }
}

/** A manual Vivo check: tick box (the whole row toggles), directions, and a shortcut to App info. */
@Composable
private fun VivoRow(item: SetupItem, onOpen: () -> Unit, onTick: (Boolean) -> Unit) {
    val text = item.check.rowText()
    val ticked = item.reason == SetupReason.CONFIRMED_BY_OWNER
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget)
                .toggleable(value = ticked, role = Role.Checkbox, onValueChange = onTick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = ticked, onCheckedChange = null)
            Column(modifier = Modifier.padding(start = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(text.title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (!ticked) {
                    text.howTo?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        if (SystemSettingsLauncher.hasSettingsPage(item.check)) {
            IconButton(onClick = onOpen) {
                Icon(
                    painterResource(R.drawable.ic_open_external),
                    stringResource(R.string.health_open_settings_for, stringResource(text.title)),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun FixAllDialog(prompt: FixAllPrompt, onOpen: (SetupCheck) -> Unit, onStop: () -> Unit) {
    when (prompt) {
        is FixAllPrompt.Next -> {
            val title = stringResource(prompt.check.rowText().title)
            AlertDialog(
                onDismissRequest = onStop,
                title = { Text(stringResource(R.string.health_fix_next_title, title)) },
                text = { Text(stringResource(R.string.health_fix_next_body)) },
                confirmButton = { TextButton(onClick = { onOpen(prompt.check) }) { Text(stringResource(R.string.health_fix_next_open)) } },
                dismissButton = { TextButton(onClick = onStop) { Text(stringResource(R.string.health_fix_stop)) } },
            )
        }
        is FixAllPrompt.Finished -> {
            val names = prompt.stillRed.map { stringResource(it.rowText().title) }
            AlertDialog(
                onDismissRequest = onStop,
                title = { Text(stringResource(R.string.health_fix_done_title)) },
                text = {
                    Text(
                        if (names.isEmpty()) stringResource(R.string.health_fix_done_all_green)
                        else stringResource(R.string.health_fix_done_still_red, names.joinToString(", ")),
                    )
                },
                confirmButton = { TextButton(onClick = onStop) { Text(stringResource(R.string.ok)) } },
            )
        }
    }
}
