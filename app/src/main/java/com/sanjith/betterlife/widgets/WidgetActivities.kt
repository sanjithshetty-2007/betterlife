package com.sanjith.betterlife.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.parser.AmPm
import com.sanjith.betterlife.domain.parser.QuickAddParser
import com.sanjith.betterlife.domain.parser.QuickAddResult
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.time.zonedNow
import com.sanjith.betterlife.domain.widget.CalendarWidgetMode
import com.sanjith.betterlife.feature.tasks.DueText
import com.sanjith.betterlife.feature.tasks.QuickAddBar
import com.sanjith.betterlife.feature.tasks.TaskService
import com.sanjith.betterlife.ui.theme.BetterLifeTheme
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Quick add from the task widget's + (Prompt 20). Widgets cannot show a keyboard, so this small
 * dialog-styled activity opens over the home screen with the same field, preview and AM/PM question
 * as the Tasks screen; Add saves through TaskService (reminders included) and closes it.
 */
public class QuickAddViewModel(private val service: TaskService, private val clock: Clock) : ViewModel() {
    private val text = MutableStateFlow("")
    private val amPm = MutableStateFlow<AmPm?>(null)
    public val typed: StateFlow<String> = text

    public val preview: StateFlow<QuickAddResult?> = combine(text, amPm) { t, answer ->
        t.takeIf { it.isNotBlank() }?.let { QuickAddParser.parse(it, clock.zonedNow(), answer) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The new task's id once saved; the activity closes then. */
    public val saved: MutableStateFlow<String?> = MutableStateFlow(null)

    public fun onText(value: String) {
        text.value = value
        amPm.value = null
    }

    public fun answer(value: AmPm) {
        amPm.value = value
    }

    /** Saves when the preview is ready; the parse is done here too, so a fast Add never saves stale text. */
    public suspend fun save(): String? {
        val result = text.value.takeIf { it.isNotBlank() }?.let { QuickAddParser.parse(it, clock.zonedNow(), amPm.value) }
            ?.takeIf { it.isReadyToSave } ?: return null
        return service.quickAdd(result).also { saved.value = it }
    }
}

public class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as BetterLifeApp).container
        val viewModel = QuickAddViewModel(container.taskService, container.clock)
        setContent {
            BetterLifeTheme {
                val context = LocalContext.current
                val dueText = remember { DueText(context) { container.clock.zone } }
                val typed by viewModel.typed.collectAsStateWithLifecycle()
                val preview by viewModel.preview.collectAsStateWithLifecycle()
                DialogCard(stringResource(R.string.widget_quick_add_title)) {
                    QuickAddBar(
                        text = typed,
                        preview = preview,
                        formatDate = { dueText.day(it, container.clock.zonedNow().toLocalDate()) },
                        formatTime = dueText::time,
                        onText = viewModel::onText,
                        onAmPm = viewModel::answer,
                        onSubmit = { lifecycleScope.launch { if (viewModel.save() != null) finish() } },
                    )
                    TextButton(onClick = ::finish, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.widget_quick_add_cancel)) }
                }
            }
        }
    }
}

/**
 * Setup for a new calendar widget (Prompt 20): month grid or next events. The launcher opens this when
 * the widget is added; the choice is saved for that one widget.
 */
public class CalendarWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out of the setup must not leave a half-made widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            BetterLifeTheme {
                DialogCard(stringResource(R.string.widget_calendar_setup_title)) {
                    Button(onClick = { choose(widgetId, CalendarWidgetMode.MONTH) }, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)) {
                        Text(stringResource(R.string.widget_calendar_setup_month))
                    }
                    OutlinedButton(onClick = { choose(widgetId, CalendarWidgetMode.AGENDA) }, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)) {
                        Text(stringResource(R.string.widget_calendar_setup_agenda))
                    }
                }
            }
        }
    }

    private fun choose(widgetId: Int, mode: CalendarWidgetMode) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@CalendarWidgetConfigActivity).getGlanceIdBy(widgetId)
            updateAppWidgetState(this@CalendarWidgetConfigActivity, glanceId) { it[CalendarWidget.MODE] = mode.name }
            CalendarWidget().update(this@CalendarWidgetConfigActivity, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }
}

/** A floating card over the home screen, like the app's sheets: surface colour, large corner, a title. */
@Composable
private fun DialogCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(max = Dimens.popupMaxWidth)) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}
