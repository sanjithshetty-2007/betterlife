package com.sanjith.betterlife.widgets

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.repository.HabitEditResult
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.time.habitDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** The system's entry point for the habit widget (declared in the manifest with res/xml/habit_widget_info.xml). */
public class HabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidget()
}

/** A box tap: tick or clear today through the same path as the app, then redraw the widget. */
public class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val habitId = parameters[HABIT_ID] ?: return
        val container = (context.applicationContext as BetterLifeApp).container
        val result = container.habitWidgetToggle.toggle(habitId)
        // Only the outcome is logged, never the habit's name (agent.md section 3).
        if (result !is HabitEditResult.Saved) Log.i(TAG, "Widget tap not saved: ${result::class.simpleName}")
        HabitWidget().updateAll(context)
    }

    public companion object {
        public val HABIT_ID: ActionParameters.Key<String> = ActionParameters.Key("habitId")
        private const val TAG = "HabitWidget"
    }
}

/**
 * Keeps every BetterLife widget current without polling (Prompts 19 and 20): each redraws when its own
 * data or the widget settings change (a tick in the app, a reminder's Done, a sync pull, an edit), and
 * all of them once at the day rollover hour so the morning shows a fresh day. Started when the app's
 * process starts, which includes a start caused by a widget itself.
 */
public class WidgetUpdates(
    private val context: Context,
    private val scope: CoroutineScope,
    private val habits: HabitWidgetSource,
    private val tasks: TaskWidgetSource,
    private val calendar: CalendarWidgetSource,
    private val clock: Clock,
    private val rolloverHour: suspend () -> Int,
) {
    private val started = AtomicBoolean(false)

    @OptIn(FlowPreview::class)
    public fun start() {
        if (!started.compareAndSet(false, true)) return
        // The first value is the state each widget was just drawn from; redraw only on changes.
        scope.launch {
            habits.observe().map { it.habits to it.hideDetails }.distinctUntilChanged().drop(1)
                .debounce(DEBOUNCE_MS).collect { HabitWidget().updateAll(context) }
        }
        scope.launch {
            tasks.observe().map { it.tasks to it.hideDetails }.distinctUntilChanged().drop(1)
                .debounce(DEBOUNCE_MS).collect { TaskWidget().updateAll(context) }
        }
        scope.launch {
            calendar.observe().map { listOf(it.items, it.categories, it.weekStart, it.hideDetails) }.distinctUntilChanged().drop(1)
                .debounce(DEBOUNCE_MS).collect { CalendarWidget().updateAll(context) }
        }
        scope.launch { scheduleRollover() }
    }

    /** Every widget at once: the rollover run, and after a restore or a big change. */
    public suspend fun updateAll() {
        HabitWidget().updateAll(context)
        TaskWidget().updateAll(context)
        CalendarWidget().updateAll(context)
    }

    /** One-off work at the next rollover hour; the work redraws the widget and books the next one. */
    public suspend fun scheduleRollover() {
        val hour = rolloverHour()
        val now = clock.now()
        val nextDay = habitDay(now, clock.zone, hour).plusDays(1)
        val at = nextDay.atTime(hour, 0).atZone(clock.zone).toInstant().plus(Duration.ofMinutes(ROLLOVER_MARGIN_MINUTES))
        val delay = Duration.between(now, at).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<HabitWidgetRolloverWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(ROLLOVER_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val DEBOUNCE_MS = 500L
        const val ROLLOVER_WORK = "habit-widget-rollover"

        // A minute after the hour, so the new habit day has certainly begun.
        const val ROLLOVER_MARGIN_MINUTES = 1L
    }
}

/** Runs once at the rollover hour: a fresh day on every widget, then the next day's run is booked. */
public class HabitWidgetRolloverWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val updates = (applicationContext as BetterLifeApp).container.widgetUpdates
        updates.updateAll()
        updates.scheduleRollover()
        return Result.success()
    }
}
