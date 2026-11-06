package com.sanjith.betterlife.feature.today

import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.entity.EventEntity
import com.sanjith.betterlife.data.db.entity.HabitEntity
import com.sanjith.betterlife.data.db.entity.HabitLogEntity
import com.sanjith.betterlife.data.db.entity.PauseRangeEntity
import com.sanjith.betterlife.data.db.entity.StandaloneReminderEntity
import com.sanjith.betterlife.data.db.entity.TaskEntity
import com.sanjith.betterlife.data.repository.due
import com.sanjith.betterlife.domain.event.Agenda
import com.sanjith.betterlife.domain.event.AgendaKind
import com.sanjith.betterlife.domain.habit.Consistency
import com.sanjith.betterlife.domain.habit.ConsistencyCalculator
import com.sanjith.betterlife.domain.habit.DayState
import com.sanjith.betterlife.domain.habit.HabitCategory
import com.sanjith.betterlife.domain.habit.HabitHistory
import com.sanjith.betterlife.domain.habit.PausePeriod
import com.sanjith.betterlife.domain.habit.StreakCalculator
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.task.TaskBucket
import com.sanjith.betterlife.domain.task.TaskDue
import com.sanjith.betterlife.domain.task.TaskFacts
import com.sanjith.betterlife.domain.task.TaskRules
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.today.GentleHabit
import com.sanjith.betterlife.domain.today.TodayEvent
import com.sanjith.betterlife.domain.today.TodayHabit
import com.sanjith.betterlife.domain.today.TodayKind
import com.sanjith.betterlife.domain.today.TodayRanking
import com.sanjith.betterlife.domain.today.TodayRow
import com.sanjith.betterlife.domain.today.TodayTask
import com.sanjith.betterlife.domain.today.TodayTier
import com.sanjith.betterlife.domain.today.UrgentReminder
import com.sanjith.betterlife.feature.events.toAgendaItem
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

/** Everything the Today screen shows at one moment (Prompt 17). */
public data class TodaySnapshot(
    val today: LocalDate,
    val rows: List<TodayRow>,
    val tasksDone: Int,
    val tasksTotal: Int,
    val habitsDone: Int,
    val habitsTotal: Int,
    val consistency: Consistency,
    val bestStreak: Int,
    val habits: List<GentleHabit>,
    val histories: List<HabitHistory>,
    val activePause: PauseRangeEntity?,
    /** Titles of open tasks and of tasks completed today, for Minimum Day and Comeback. */
    val taskTitles: Map<String, String> = emptyMap(),
    val tasksDoneToday: Set<String> = emptySet(),
) {
    /** Today's progress: tasks and habits together. */
    val progressPercent: Int
        get() = if (tasksTotal + habitsTotal == 0) 0 else ((tasksDone + habitsDone) * PERCENT / (tasksTotal + habitsTotal).toDouble()).roundToInt()

    /** The most important task (the first task on the ranking), for Minimum Day and Comeback. */
    val topTaskId: String?
        get() = rows.flatMap { row ->
            when (row) {
                is TodayRow.Single -> listOf(row.item)
                is TodayRow.Group -> row.items
            }
        }.firstOrNull { it.kind == TodayKind.TASK }?.id

    private companion object {
        const val PERCENT = 100
    }
}

/** The raw rows the snapshot is built from. */
public data class TodayData(
    val tasks: List<TaskEntity>,
    val events: List<EventEntity>,
    val standalone: List<StandaloneReminderEntity>,
    val habits: List<HabitEntity>,
    val logs: List<HabitLogEntity>,
    val pauses: List<PauseRangeEntity>,
)

/**
 * Builds the Today snapshot from the database rows. [clock] is the app clock (the Time Machine may
 * move it); [today] is the habit day after the rollover hour.
 */
public class TodaySource(private val db: BetterLifeDatabase, private val clock: Clock) {

    public suspend fun snapshot(data: TodayData, today: LocalDate): TodaySnapshot {
        val now = clock.now()
        val nowZoned = now.atZone(clock.zone)
        val calendarToday = nowZoned.toLocalDate()
        val periods = data.pauses.map { PausePeriod(it.startDate, it.endDate) }

        val open = data.tasks.filter { it.completedAt == null }
        val todayTasks = open.map { task ->
            val bucket = TaskRules.bucket(TaskFacts(task.due, task.projectId != null, completed = false, recurrence = null), nowZoned)
            TodayTask(task.id, task.title, task.priority, (task.due as? TaskDue.At)?.instant, bucket == TaskBucket.TODAY, bucket == TaskBucket.OVERDUE)
        }
        val doneTodayIds = data.tasks.filter { it.completedAt != null && Instant.ofEpochMilli(it.completedAt).atZone(clock.zone).toLocalDate() == calendarToday }
        val doneToday = doneTodayIds.size

        val logsByHabit = data.logs.groupBy { it.habitId }
        val histories = data.habits.associate { h ->
            h.id to HabitHistory(h.startDate, logsByHabit[h.id].orEmpty().associate { it.localDate to it.status }, periods)
        }
        val gentle = data.habits.map { h ->
            val history = histories.getValue(h.id)
            val state = history.state(today, today)
            GentleHabit(
                id = h.id,
                name = h.name,
                essential = h.essential,
                selfCare = h.category == HabitCategory.SELF_CARE,
                currentStreak = StreakCalculator.current(history, today),
                settledToday = state == DayState.DONE || state == DayState.KEPT || state == DayState.PAUSED,
            )
        }
        val habitRows = data.habits.zip(gentle).filter { !it.second.settledToday }.map { (h, _) -> TodayHabit(h.id, h.name, h.reminderTime) }

        val rows = TodayRanking.rank(
            now = now,
            nowTime = nowZoned.toLocalTime(),
            urgent = urgent(open, data.standalone, now),
            tasks = todayTasks,
            events = Agenda.build(data.events.map { it.toAgendaItem() }, calendarToday, EVENT_DAYS, clock.zone)
                .flatMap { it.entries }
                .filter { (it.item.kind == AgendaKind.EVENT || it.item.kind == AgendaKind.MEETING) && it.start != null }
                .map { TodayEvent(it.item.id, it.item.title, it.start!!) },
            habits = habitRows,
        )
        return TodaySnapshot(
            today = today,
            rows = rows,
            tasksDone = doneToday,
            tasksTotal = doneToday + todayTasks.count { it.dueToday },
            habitsDone = gentle.count { it.settledToday },
            habitsTotal = gentle.size,
            consistency = overallConsistency(histories.values.toList(), today),
            bestStreak = gentle.maxOfOrNull { it.currentStreak } ?: 0,
            habits = gentle,
            histories = histories.values.toList(),
            activePause = data.pauses.firstOrNull { PausePeriod(it.startDate, it.endDate).contains(today) },
            taskTitles = (open + doneTodayIds).associate { it.id to it.title },
            tasksDoneToday = doneTodayIds.map { it.id }.toSet(),
        )
    }

    /** Pop-up and alarm reminders of open tasks and remind-me items with their fire time near now. */
    private suspend fun urgent(open: List<TaskEntity>, standalone: List<StandaloneReminderEntity>, now: Instant): List<UrgentReminder> {
        val tasks = open.associateBy { it.id }
        val items = standalone.filter { it.doneAt == null }.associateBy { it.id }
        val window = now.minus(TodayRanking.URGENT_BACK)..now.plus(TodayRanking.URGENT_AHEAD)
        return db.reminderDao().getSchedulable().filter { it.importance != Importance.NORMAL }.mapNotNull { r ->
            val fireAt = listOfNotNull(r.lastFiredFireAt, r.scheduledFireAt).map(Instant::ofEpochMilli).firstOrNull { it in window } ?: return@mapNotNull null
            when (r.itemType) {
                ReminderItemType.TASK -> tasks[r.itemId]?.let { UrgentReminder(TodayKind.TASK, it.id, it.title, fireAt) }
                ReminderItemType.STANDALONE -> items[r.itemId]?.let { UrgentReminder(TodayKind.REMINDER, it.id, it.title, fireAt) }
                else -> null
            }
        }
    }

    /** All habits together: done or kept days over scheduled days in the last 30; "New" under 7 days of data. */
    private fun overallConsistency(histories: List<HabitHistory>, today: LocalDate): Consistency {
        val parts = histories.map { ConsistencyCalculator.calculate(it, today) }.filterIsInstance<Consistency.Percent>()
        val scheduled = parts.sumOf { it.scheduled }
        if (scheduled < ConsistencyCalculator.MIN_DAYS_OF_DATA) return Consistency.New
        val completed = parts.sumOf { it.completed }
        return Consistency.Percent((completed * PERCENT_DOUBLE / scheduled).roundToInt(), completed, scheduled)
    }

    private companion object {
        const val EVENT_DAYS = 2
        const val PERCENT_DOUBLE = 100.0
    }
}

/** Whether a row's item is a tier the owner can tick from Today (tasks and habits). */
internal val TodayTier.tickable: Boolean get() = this == TodayTier.OVERDUE_HIGH || this == TodayTier.TODAY_TASK || this == TodayTier.HABIT
