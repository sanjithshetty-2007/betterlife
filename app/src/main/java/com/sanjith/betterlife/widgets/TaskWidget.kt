package com.sanjith.betterlife.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.MainActivity
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.widget.TaskWidgetBuilder
import com.sanjith.betterlife.domain.widget.TaskWidgetModel
import com.sanjith.betterlife.domain.widget.TaskWidgetRow
import com.sanjith.betterlife.feature.tasks.DueText
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import com.sanjith.betterlife.ui.theme.WidgetType

/**
 * The task widget (Prompt 20, context.md 5.8): today's and overdue tasks; tick done, delete after a
 * confirm tap (a widget cannot show an Undo bar), and + opens the quick-add popup.
 */
public class TaskWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as BetterLifeApp).container
        val source = container.taskWidgetSource
        val dueText = DueText(context) { container.clock.zone }
        provideContent {
            val input by remember { source.observe() }.collectAsState(initial = null)
            val model = input?.let { TaskWidgetBuilder.build(it.tasks, it.now, it.hideDetails) }
            WidgetCard { TaskContent(model, currentState(PENDING_DELETE), dueText) }
        }
    }

    public companion object {
        /** The task whose bin was tapped once: its row asks "Delete?" until confirmed or kept. */
        internal val PENDING_DELETE = stringPreferencesKey("pending_delete")
    }
}

public class TaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskWidget()
}

@Composable
private fun TaskContent(model: TaskWidgetModel?, pendingDelete: String?, dueText: DueText) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
        Text(context.getString(R.string.widget_tasks_header), style = TextStyle(color = WidgetColors.muted, fontSize = WidgetType.header, fontWeight = FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier.size(Dimens.minTouchTarget).clickable(actionStartActivity<QuickAddActivity>())
                .semantics { contentDescription = context.getString(R.string.widget_tasks_add) },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = GlanceModifier.size(Dimens.habitBox).cornerRadius(Dimens.habitBoxCorner).background(WidgetColors.accent)) {
                Image(ImageProvider(R.drawable.ic_add), contentDescription = null, colorFilter = ColorFilter.tint(WidgetColors.onAccent), modifier = GlanceModifier.size(Dimens.iconSize))
            }
        }
    }
    when {
        model == null -> Unit
        model.isEmpty -> WidgetEmpty(context.getString(R.string.widget_tasks_empty), MainActivity.openRouteIntent(context, MainActivity.ROUTE_TASKS))
        else -> LazyColumn {
            items(model.rows, itemId = { it.number.toLong() }) { row ->
                if (row.taskId == pendingDelete) ConfirmDeleteRow(row) else TaskRow(row, model, dueText)
            }
        }
    }
}

/** A done circle, the title (or "Task N") with its deadline, the priority flag and the bin. */
@Composable
private fun TaskRow(row: TaskWidgetRow, model: TaskWidgetModel, dueText: DueText) {
    val context = LocalContext.current
    val title = row.title ?: context.getString(R.string.widget_task_number, row.number)
    val due = dueText.format(row.due, null, model.today).orEmpty()
    val level = priorityWord(row.priority)?.let(context::getString)
    // The priority word as well as the flag's colour, never colour alone (as the task rows do).
    val dueLine = listOfNotNull(if (row.overdue) context.getString(R.string.widget_task_overdue, due) else due.ifEmpty { null }, level).joinToString(" · ")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth().padding(vertical = Dimens.gridBlockGap)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier.size(Dimens.minTouchTarget).clickable(actionRunCallback<CompleteTaskAction>(taskParams(row.taskId)))
                .semantics { contentDescription = context.getString(R.string.widget_task_complete, title) },
        ) {
            Box(GlanceModifier.size(Dimens.doneMark).cornerRadius(Dimens.doneMark).background(WidgetColors.openBorder)) {
                Box(GlanceModifier.size(Dimens.doneMark - Dimens.borderWidth * 2).cornerRadius(Dimens.doneMark).background(WidgetColors.surface)) {}
            }
        }
        Column(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.defaultWeight().height(Dimens.minTouchTarget)
                .clickable(actionStartActivity(MainActivity.openItemIntent(context, ReminderItemType.TASK, row.taskId))),
        ) {
            Text(title, style = TextStyle(color = WidgetColors.text, fontSize = WidgetType.header), maxLines = 1)
            if (dueLine.isNotEmpty()) {
                Text(dueLine, style = TextStyle(color = if (row.overdue) WidgetColors.bad else WidgetColors.muted, fontSize = WidgetType.label), maxLines = 1)
            }
        }
        flagColor(row.priority)?.let { color ->
            Image(
                ImageProvider(R.drawable.ic_flag), contentDescription = level?.let { context.getString(R.string.widget_task_priority, it) },
                colorFilter = ColorFilter.tint(ColorProvider(color)), modifier = GlanceModifier.size(Dimens.smallIconSize),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier.size(Dimens.minTouchTarget).clickable(actionRunCallback<AskDeleteAction>(taskParams(row.taskId)))
                .semantics { contentDescription = context.getString(R.string.widget_task_delete, title) },
        ) {
            Image(ImageProvider(R.drawable.ic_delete), contentDescription = null, colorFilter = ColorFilter.tint(WidgetColors.muted), modifier = GlanceModifier.size(Dimens.iconSize))
        }
    }
}

/** The second step of a delete: "Delete this task?" with Delete and Keep. */
@Composable
private fun ConfirmDeleteRow(row: TaskWidgetRow) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth().height(Dimens.minTouchTarget)) {
        Text(context.getString(R.string.widget_task_delete_ask), style = TextStyle(color = WidgetColors.text, fontSize = WidgetType.header), maxLines = 1, modifier = GlanceModifier.defaultWeight().padding(start = Spacing.sm))
        WidgetButton(context.getString(R.string.widget_task_delete_yes), WidgetColors.bad, actionRunCallback<ConfirmDeleteAction>(taskParams(row.taskId)))
        Spacer(GlanceModifier.size(Spacing.xs))
        WidgetButton(context.getString(R.string.widget_task_delete_keep), WidgetColors.muted, actionRunCallback<CancelDeleteAction>())
    }
}

private fun priorityWord(priority: Priority): Int? = when (priority) {
    Priority.HIGH -> R.string.priority_high
    Priority.MEDIUM -> R.string.priority_medium
    Priority.LOW -> R.string.priority_low
    Priority.NONE -> null
}

private fun flagColor(priority: Priority): Color? = when (priority) {
    Priority.HIGH -> BetterLifeColors.StatusBad
    Priority.MEDIUM -> BetterLifeColors.StatusWarn
    Priority.LOW -> BetterLifeColors.StatusGood
    Priority.NONE -> null
}

private val TASK_ID = ActionParameters.Key<String>("taskId")

private fun taskParams(taskId: String) = actionParametersOf(TASK_ID to taskId)

/** Done: the same complete as the Tasks screen (next instance for a repeating task, reminders stop). */
public class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TASK_ID] ?: return
        (context.applicationContext as BetterLifeApp).container.taskWidgetActions.complete(id)
        TaskWidget().update(context, glanceId)
    }
}

/** First tap on the bin: the row asks to confirm. */
public class AskDeleteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TASK_ID] ?: return
        updateAppWidgetState(context, glanceId) { it[TaskWidget.PENDING_DELETE] = id }
        TaskWidget().update(context, glanceId)
    }
}

/** Confirmed: the same soft delete as the Tasks screen (reminders cancelled, restorable from the app later). */
public class ConfirmDeleteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TASK_ID] ?: return
        updateAppWidgetState(context, glanceId) { it.remove(TaskWidget.PENDING_DELETE) }
        (context.applicationContext as BetterLifeApp).container.taskWidgetActions.delete(id)
        TaskWidget().update(context, glanceId)
    }
}

public class CancelDeleteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { it.remove(TaskWidget.PENDING_DELETE) }
        TaskWidget().update(context, glanceId)
    }
}
