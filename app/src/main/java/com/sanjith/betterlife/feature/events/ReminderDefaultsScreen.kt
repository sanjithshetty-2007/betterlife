package com.sanjith.betterlife.feature.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.reminder.ReminderDefaults
import com.sanjith.betterlife.domain.task.ReminderPlan
import com.sanjith.betterlife.feature.tasks.DueText
import com.sanjith.betterlife.feature.tasks.RemindersSection
import com.sanjith.betterlife.feature.tasks.SectionLabel
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * The default early reminders for new items (context.md 5.1), plus the time birthday and all-day
 * reminders fire at. Saved as soon as something changes. Moves into Settings in Prompt 27.
 */
@Composable
public fun ReminderDefaultsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as BetterLifeApp).container
    val dueText = remember { DueText(context) { container.clock.zone } }
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    val update: ((ReminderDefaults) -> ReminderDefaults) -> Unit = { change ->
        scope.launch { container.settingsRepository.update { it.copy(reminderDefaults = change(it.reminderDefaults)) } }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.setup_back), tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(stringResource(R.string.defaults_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            }
            Text(stringResource(R.string.defaults_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val s = settings ?: return@Column
            val d = s.reminderDefaults

            SectionLabel(stringResource(R.string.defaults_birthday_time))
            TimeButton(dueText.time(s.birthdayFireTime), s.birthdayFireTime, onPick = { t ->
                t?.let { time -> scope.launch { container.settingsRepository.update { it.copy(birthdayFireTime = time) } } }
            })
            DefaultsGroup(R.string.defaults_birthdays, d.birthday) { list -> update { it.copy(birthday = list) } }
            DefaultsGroup(R.string.defaults_deadlines, d.deadline) { list -> update { it.copy(deadline = list) } }
            DefaultsGroup(R.string.defaults_events, d.event) { list -> update { it.copy(event = list) } }
            DefaultsGroup(R.string.defaults_standalone, d.standalone) { list -> update { it.copy(standalone = list) } }
        }
    }
}

@Composable
private fun DefaultsGroup(title: Int, plans: List<ReminderPlan>, onChange: (List<ReminderPlan>) -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    RemindersSection(plans, defaultLevel = Importance.NORMAL, onChange = onChange)
}
