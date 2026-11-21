package com.sanjith.betterlife.feature.protein

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.R
import com.sanjith.betterlife.feature.habits.HabitAvatar
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing

/**
 * Protein at the top of the Habits tab: today's grams of the target and the streak, with a bar.
 * Tapping it opens the protein dashboard. Before a goal is set it invites setting one.
 */
@Composable
public fun ProteinHabitsCard(onOpen: () -> Unit, viewModel: ProteinViewModel = viewModel(factory = ProteinViewModel.Factory)) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val state = ui ?: return
    val color = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable(role = Role.Button, onClick = onOpen)
                .heightIn(min = Dimens.minTouchTarget)
                .padding(vertical = Spacing.md, horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                HabitAvatar(stringResource(R.string.protein_avatar), color)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.protein_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = summary(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val target = state.todayTarget
            if (state.hasGoal && target != null) {
                LinearProgressIndicator(progress = { ((state.todayGrams ?: 0).toFloat() / target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun summary(state: ProteinUi): String {
    val target = state.todayTarget
    if (!state.hasGoal || target == null) return stringResource(R.string.protein_card_setup)
    val today = state.todayGrams?.let { stringResource(R.string.protein_today_amount, it, target) } ?: stringResource(R.string.protein_not_logged)
    val streak = state.streaks.current
    return if (streak > 0) today + " · " + pluralStringResource(R.plurals.protein_streak_days, streak, streak) else today
}
