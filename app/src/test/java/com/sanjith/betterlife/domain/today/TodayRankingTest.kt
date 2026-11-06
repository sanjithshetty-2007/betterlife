package com.sanjith.betterlife.domain.today

import com.sanjith.betterlife.domain.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

/** The Today ranking rule of context.md 5.5 (Prompt 17). Now = Fri 20 Nov 2026 10:00 UTC. */
public class TodayRankingTest {

    private val now = Instant.parse("2026-11-20T10:00:00Z")
    private val nowTime = LocalTime.of(10, 0)

    private fun t(hhmm: String): Instant = Instant.parse("2026-11-20T$hhmm:00Z")

    private fun ids(rows: List<TodayRow>): List<String> = rows.map { row ->
        when (row) {
            is TodayRow.Single -> row.item.id
            is TodayRow.Group -> row.items.joinToString("+") { it.id }
        }
    }

    private fun rank(
        urgent: List<UrgentReminder> = emptyList(),
        tasks: List<TodayTask> = emptyList(),
        events: List<TodayEvent> = emptyList(),
        habits: List<TodayHabit> = emptyList(),
    ) = TodayRanking.rank(now, nowTime, urgent, tasks, events, habits)

    @Test
    public fun theFiveTiersComeInOrder() {
        val rows = rank(
            urgent = listOf(UrgentReminder(TodayKind.TASK, "pill", "Pill", t("09:55"))),
            tasks = listOf(
                TodayTask("today", "Today task", Priority.LOW, t("15:00"), dueToday = true, overdue = false),
                TodayTask("late", "Late high", Priority.HIGH, t("08:00"), dueToday = false, overdue = true),
            ),
            events = listOf(TodayEvent("meet", "Meeting", t("11:00"))),
            habits = listOf(TodayHabit("hab", "Meditation", null)),
        )
        assertEquals(listOf("pill", "late", "meet", "today", "hab"), ids(rows))
        assertEquals(
            listOf(TodayTier.URGENT_REMINDER, TodayTier.OVERDUE_HIGH, TodayTier.EVENT_SOON, TodayTier.TODAY_TASK, TodayTier.HABIT),
            rows.map { it.tier },
        )
    }

    @Test
    public fun urgentRemindersCountFromTwoHoursAgoToFifteenMinutesAhead() {
        val rows = rank(
            urgent = listOf(
                UrgentReminder(TodayKind.TASK, "old", "Too old", t("07:59")),
                UrgentReminder(TodayKind.TASK, "edge", "Two hours ago", t("08:00")),
                UrgentReminder(TodayKind.REMINDER, "soon", "In 15 minutes", t("10:15")),
                UrgentReminder(TodayKind.TASK, "later", "In 16 minutes", t("10:16")),
            ),
        )
        assertEquals(listOf("edge", "soon"), ids(rows))
    }

    @Test
    public fun onlyOverdueHighTasksJumpTheQueueOldestFirst() {
        val rows = rank(
            tasks = listOf(
                TodayTask("mid", "Overdue medium", Priority.MEDIUM, t("07:00"), dueToday = false, overdue = true),
                TodayTask("h2", "Newer high", Priority.HIGH, t("09:00"), dueToday = false, overdue = true),
                TodayTask("h1", "Older high", Priority.HIGH, t("06:00"), dueToday = false, overdue = true),
            ),
        )
        assertEquals(listOf("h1", "h2"), ids(rows))
    }

    @Test
    public fun eventsWithinTwoHoursOnlyAndNotOnesAlreadyStarted() {
        val rows = rank(events = listOf(TodayEvent("past", "Started", t("09:30")), TodayEvent("b", "B", t("12:00")), TodayEvent("a", "A", t("10:30")), TodayEvent("far", "Far", t("12:01"))))
        assertEquals(listOf("a", "b"), ids(rows))
    }

    @Test
    public fun todaysTasksGoByPriorityThenTimeWithUntimedLast() {
        val rows = rank(
            tasks = listOf(
                TodayTask("lowEarly", "Low early", Priority.LOW, t("11:00"), dueToday = true, overdue = false),
                TodayTask("highUntimed", "High no time", Priority.HIGH, null, dueToday = true, overdue = false),
                TodayTask("highLate", "High late", Priority.HIGH, t("18:00"), dueToday = true, overdue = false),
                TodayTask("highEarly", "High early", Priority.HIGH, t("12:00"), dueToday = true, overdue = false),
            ),
        )
        assertEquals(listOf("highEarly", "highLate", "highUntimed", "lowEarly"), ids(rows))
    }

    @Test
    public fun tasksAtTheSameMinuteCollapseIntoOneRow() {
        val rows = rank(
            tasks = listOf(
                TodayTask("gym", "Gym", Priority.NONE, t("14:00"), dueToday = true, overdue = false),
                TodayTask("mom", "Call mom", Priority.NONE, t("14:00"), dueToday = true, overdue = false),
                TodayTask("x", "Other", Priority.NONE, t("15:00"), dueToday = true, overdue = false),
            ),
        )
        assertEquals(listOf("gym+mom", "x"), ids(rows))
        assertEquals(t("14:00"), (rows.first() as TodayRow.Group).minute)
    }

    @Test
    public fun habitsWhoseTimeHasPassedComeFirstThenUntimedThenLater() {
        val rows = rank(
            habits = listOf(
                TodayHabit("night", "Skincare", LocalTime.of(22, 0)),
                TodayHabit("none", "Haircare", null),
                TodayHabit("morning", "Creatine", LocalTime.of(8, 0)),
                TodayHabit("dawn", "Meditation", LocalTime.of(6, 30)),
            ),
        )
        assertEquals(listOf("dawn", "morning", "none", "night"), ids(rows))
    }

    @Test
    public fun anItemAppearsOnlyInItsHighestTier() {
        val rows = rank(
            urgent = listOf(UrgentReminder(TodayKind.TASK, "pay", "Pay bill", t("10:00"))),
            tasks = listOf(TodayTask("pay", "Pay bill", Priority.HIGH, t("10:00"), dueToday = true, overdue = false)),
        )
        assertEquals(listOf("pay"), ids(rows))
        assertEquals(TodayTier.URGENT_REMINDER, rows.single().tier)
    }

    @Test
    public fun nothingToDoIsAnEmptyList() {
        assertEquals(emptyList<TodayRow>(), rank())
    }
}
