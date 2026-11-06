package com.sanjith.betterlife.feature.today

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.settings.AppSettings
import com.sanjith.betterlife.feature.debug.moodLabel
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import com.sanjith.betterlife.domain.mascot.MascotMood
import com.sanjith.betterlife.domain.today.Comeback
import com.sanjith.betterlife.domain.today.MinimumDay
import com.sanjith.betterlife.ui.mascot.Mascot
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing

/** One thing to do on Minimum Day or the Comeback mission. */
private data class GentleItem(val id: String, val isTask: Boolean, val kind: Int, val title: String, val done: Boolean)

/**
 * Minimum Day (Prompt 17): three things picked by rule. When all are done every habit not done today
 * becomes a KEPT day, Blip cheers and says that's enough.
 */
@Composable
public fun MinimumDayScreen(onClose: () -> Unit, viewModel: TodayViewModel = viewModel(factory = TodayViewModel.GentleFactory)) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snapshot = ui?.snapshot
    // The pick is made once when the screen opens, so ticking an item does not swap it for another.
    var pick by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(snapshot != null) {
        if (snapshot != null && pick == null) {
            val p = MinimumDay.pick(snapshot.topTaskId, snapshot.habits)
            pick = listOf(p.taskId.orEmpty(), p.habitId.orEmpty(), p.selfCareId.orEmpty())
        }
    }
    val items = snapshot?.let { s ->
        val (task, habit, selfCare) = pick ?: return@let null
        listOfNotNull(
            task.takeIf { it.isNotEmpty() }?.let { GentleItem(it, true, R.string.minimum_kind_task, s.taskTitles[it].orEmpty(), it in s.tasksDoneToday) },
            habit.takeIf { it.isNotEmpty() }?.let { id ->
                val h = s.habits.first { it.id == id }
                GentleItem(id, false, if (h.essential) R.string.minimum_kind_essential else R.string.minimum_kind_habit, h.name, h.settledToday)
            },
            selfCare.takeIf { it.isNotEmpty() }?.let { id -> s.habits.first { it.id == id }.let { GentleItem(id, false, R.string.minimum_kind_self_care, it.name, it.settledToday) } },
        )
    }
    val allDone = items != null && items.isNotEmpty() && items.all { it.done }
    LaunchedEffect(allDone) { if (allDone) viewModel.keepTheRest() }

    GentleScaffold(onClose) {
        val mood = if (allDone) MascotMood.CHEERING else MascotMood.PROUD
        Mascot(mood, ui?.stage ?: com.sanjith.betterlife.domain.mascot.MascotStage.BABY, mascotDescription(mood), Modifier.size(Dimens.mascotLarge))
        val count = items?.size ?: MINIMUM_ITEMS
        Title(if (allDone) stringResource(R.string.minimum_done) else pluralStringResource(R.plurals.minimum_title, count, count))
        Subtitle(stringResource(if (allDone) R.string.minimum_done_subtitle else R.string.minimum_subtitle))
        if (items != null && items.isEmpty()) Subtitle(stringResource(R.string.minimum_empty))
        items?.forEach { item ->
            GentleCard(item) {
                if (item.isTask) viewModel.completeTask(item.id) else viewModel.tickHabit(item.id, HabitLogStatus.KEPT)
            }
        }
        if (!items.isNullOrEmpty()) {
            val done = items.count { it.done }
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.minimum_progress, done, items.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.minimum_counts), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(progress = { done.toFloat() / items.size }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Comeback mission (Prompt 17): two habits and the most important task. It never lists missed days. */
@Composable
public fun ComebackScreen(onClose: () -> Unit, viewModel: TodayViewModel = viewModel(factory = TodayViewModel.GentleFactory)) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snapshot = ui?.snapshot
    var mission by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(snapshot != null) {
        if (snapshot != null && mission == null) mission = Comeback.mission(snapshot.topTaskId, snapshot.habits)
    }
    val items = snapshot?.let { s ->
        mission?.mapNotNull { id ->
            s.habits.firstOrNull { it.id == id }?.let { GentleItem(id, false, R.string.comeback_kind_habit, it.name, it.settledToday) }
                ?: s.taskTitles[id]?.let { GentleItem(id, true, R.string.comeback_kind_task, it, id in s.tasksDoneToday) }
        }
    }
    GentleScaffold(onClose) {
        Mascot(MascotMood.CHEERING, ui?.stage ?: com.sanjith.betterlife.domain.mascot.MascotStage.BABY, mascotDescription(MascotMood.CHEERING), Modifier.size(Dimens.mascotLarge))
        Title(stringResource(R.string.comeback_title))
        Subtitle(stringResource(R.string.comeback_subtitle))
        items?.forEach { item ->
            GentleCard(item) { if (item.isTask) viewModel.completeTask(item.id) else viewModel.tickHabit(item.id) }
        }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)) { Text(stringResource(R.string.comeback_start)) }
    }
}

private const val MINIMUM_ITEMS = 3

/** "Blip, proud": the mascot's name and mood for TalkBack. */
@Composable
internal fun mascotDescription(mood: MascotMood): String {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(null)
    val name = settings?.mascotName ?: AppSettings.DEFAULT_MASCOT_NAME
    return stringResource(R.string.mascot_with_mood, name, stringResource(moodLabel(mood)))
}

@Composable
private fun GentleScaffold(onClose: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onClose)
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close), tint = MaterialTheme.colorScheme.onBackground)
                }
            }
            content()
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Subtitle(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

/** A card with the kind, the title and a round tick; done items are struck through. */
@Composable
private fun GentleCard(item: GentleItem, onTick: () -> Unit) {
    val state = stringResource(if (item.done) R.string.habit_box_state_done else R.string.habit_box_state_not_done)
    Card(
        onClick = onTick,
        enabled = !item.done,
        modifier = Modifier.fillMaxWidth().semantics {
            stateDescription = state
            toggleableState = if (item.done) ToggleableState.On else ToggleableState.Off
        },
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, if (item.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, disabledContainerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(item.kind), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(Dimens.iconTile).then(
                    if (item.done) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                    else Modifier.border(Dimens.borderWidth * 2, BetterLifeColors.BoxOpenBorder, CircleShape),
                ),
            ) {
                if (item.done) Icon(painterResource(R.drawable.ic_check), null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
