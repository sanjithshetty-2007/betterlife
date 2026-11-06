package com.sanjith.betterlife.domain.today

import com.sanjith.betterlife.domain.event.SameTimeGrouping
import com.sanjith.betterlife.domain.model.Priority
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/** What a Today row points at. */
public enum class TodayKind { TASK, EVENT, REMINDER, HABIT }

/** The five tiers of context.md 5.5, in order. */
public enum class TodayTier { URGENT_REMINDER, OVERDUE_HIGH, EVENT_SOON, TODAY_TASK, HABIT }

/** One thing on the Today list. [time] is what it is ranked and grouped by (may be null). */
public data class TodayItem(
    val kind: TodayKind,
    val id: String,
    val title: String,
    val tier: TodayTier,
    val time: Instant?,
    val priority: Priority = Priority.NONE,
    /** Habits only: its reminder time, shown as "time-of-day". */
    val habitTime: LocalTime? = null,
)

/** A Today row: one item, or several at the same minute ("2:00 PM, 2 items"). */
public sealed interface TodayRow {
    public val tier: TodayTier

    public data class Single(val item: TodayItem) : TodayRow {
        override val tier: TodayTier get() = item.tier
    }

    public data class Group(val minute: Instant, val items: List<TodayItem>) : TodayRow {
        override val tier: TodayTier get() = items.first().tier
    }
}

/** A task or remind-me with a pop-up or alarm reminder whose fire time is [fireAt]. */
public data class UrgentReminder(val kind: TodayKind, val id: String, val title: String, val fireAt: Instant)

/** An open task: [due] null for undated, [dueToday] / [overdue] from the Tasks buckets. */
public data class TodayTask(val id: String, val title: String, val priority: Priority, val due: Instant?, val dueToday: Boolean, val overdue: Boolean)

public data class TodayEvent(val id: String, val title: String, val start: Instant)

/** A habit not done (nor kept nor paused) today. */
public data class TodayHabit(val id: String, val name: String, val reminderTime: LocalTime?)

/**
 * "What should I do right now?" (context.md 5.5, Prompt 17), first tier first:
 * 1. pop-up / alarm reminders from 2 hours ago to 15 minutes ahead (soonest first);
 * 2. overdue HIGH tasks (oldest due first);
 * 3. events starting within the next 2 hours (soonest first);
 * 4. today's tasks by priority (high first) then time (untimed last); tasks at the same minute
 *    collapse into one row;
 * 5. habits still to do: those whose reminder time has passed (by time), then those without a time
 *    (in the owner's order), then later ones (by time).
 * Each item appears once, in its highest tier.
 */
public object TodayRanking {
    public val URGENT_BACK: Duration = Duration.ofHours(2)
    public val URGENT_AHEAD: Duration = Duration.ofMinutes(15)
    public val EVENT_SOON: Duration = Duration.ofHours(2)

    public fun rank(
        now: Instant,
        nowTime: LocalTime,
        urgent: List<UrgentReminder>,
        tasks: List<TodayTask>,
        events: List<TodayEvent>,
        habits: List<TodayHabit>,
    ): List<TodayRow> {
        val seen = HashSet<Pair<TodayKind, String>>()
        fun TodayItem.firstTime(): Boolean = seen.add(kind to id)

        val tier1 = urgent
            .filter { !it.fireAt.isBefore(now.minus(URGENT_BACK)) && !it.fireAt.isAfter(now.plus(URGENT_AHEAD)) }
            .sortedBy { it.fireAt }
            .map { TodayItem(it.kind, it.id, it.title, TodayTier.URGENT_REMINDER, it.fireAt) }
            .filter { it.firstTime() }
        val tier2 = tasks.filter { it.overdue && it.priority == Priority.HIGH }
            .sortedWith(compareBy(nullsLast()) { it.due })
            .map { TodayItem(TodayKind.TASK, it.id, it.title, TodayTier.OVERDUE_HIGH, it.due, it.priority) }
            .filter { it.firstTime() }
        val tier3 = events.filter { !it.start.isBefore(now) && !it.start.isAfter(now.plus(EVENT_SOON)) }
            .sortedBy { it.start }
            .map { TodayItem(TodayKind.EVENT, it.id, it.title, TodayTier.EVENT_SOON, it.start) }
            .filter { it.firstTime() }
        val tier4 = tasks.filter { it.dueToday }
            .sortedWith(compareByDescending<TodayTask> { it.priority.ordinal }.thenBy(nullsLast()) { it.due })
            .map { TodayItem(TodayKind.TASK, it.id, it.title, TodayTier.TODAY_TASK, it.due, it.priority) }
            .filter { it.firstTime() }
        val tier5 = habitOrder(habits, nowTime)
            .map { TodayItem(TodayKind.HABIT, it.id, it.name, TodayTier.HABIT, null, habitTime = it.reminderTime) }
            .filter { it.firstTime() }

        return (tier1 + tier2 + tier3).map(TodayRow::Single) + collapse(tier4) + tier5.map(TodayRow::Single)
    }

    private fun habitOrder(habits: List<TodayHabit>, nowTime: LocalTime): List<TodayHabit> {
        val passed = habits.filter { it.reminderTime != null && !it.reminderTime.isAfter(nowTime) }.sortedBy { it.reminderTime }
        val untimed = habits.filter { it.reminderTime == null }
        val later = habits.filter { it.reminderTime != null && it.reminderTime.isAfter(nowTime) }.sortedBy { it.reminderTime }
        return passed + untimed + later
    }

    /** Same-minute tasks become one row, at the place of the first of them (Prompt 10's helper). */
    private fun collapse(items: List<TodayItem>): List<TodayRow> =
        SameTimeGrouping.group(items) { it.time }.map { group ->
            val minute = group.minute
            if (minute != null && group.count > 1) TodayRow.Group(minute, group.items) else TodayRow.Single(group.items.single())
        }
}
