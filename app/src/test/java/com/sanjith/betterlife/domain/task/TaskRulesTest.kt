package com.sanjith.betterlife.domain.task

import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.domain.parser.QuickAddParser
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import com.sanjith.betterlife.domain.recurrence.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

public class TaskRulesTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.parse("2026-10-08T18:00").atZone(zone)
    private fun at(local: String) = TaskDue.At(LocalDateTime.parse(local).atZone(zone).toInstant(), zone)
    private fun facts(due: TaskDue, project: Boolean = false, done: Boolean = false, rule: RecurrenceRule? = null) =
        TaskFacts(due, project, done, rule)

    @Test
    public fun bucketsFollowTheDueTime() {
        assertEquals(TaskBucket.OVERDUE, TaskRules.bucket(facts(at("2026-10-08T17:00")), now))
        assertEquals(TaskBucket.TODAY, TaskRules.bucket(facts(at("2026-10-08T21:00")), now))
        assertEquals(TaskBucket.UPCOMING, TaskRules.bucket(facts(at("2026-10-09T09:00")), now))
        assertEquals(TaskBucket.OVERDUE, TaskRules.bucket(facts(TaskDue.OnDay(LocalDate.of(2026, 10, 7))), now))
        assertEquals(TaskBucket.TODAY, TaskRules.bucket(facts(TaskDue.OnDay(LocalDate.of(2026, 10, 8))), now))
        assertEquals(TaskBucket.UPCOMING, TaskRules.bucket(facts(TaskDue.OnDay(LocalDate.of(2026, 10, 9))), now))
    }

    @Test
    public fun undatedTasksGoToTheInboxUnlessInAProject() {
        assertEquals(TaskBucket.INBOX, TaskRules.bucket(facts(TaskDue.None), now))
        assertEquals(TaskBucket.PROJECT_NO_DATE, TaskRules.bucket(facts(TaskDue.None, project = true), now))
    }

    @Test
    public fun completedTasksAreDoneWhateverTheirDate() {
        assertEquals(TaskBucket.DONE, TaskRules.bucket(facts(at("2026-10-01T09:00"), done = true), now))
    }

    @Test
    public fun overdueRepeatingTaskShowsOneDateThatRollsForwardAfter30Days() {
        val daily = RecurrenceRule.Daily()
        assertEquals(LocalDate.of(2026, 9, 20), TaskRules.displayDate(facts(TaskDue.OnDay(LocalDate.of(2026, 9, 20)), rule = daily), now.toLocalDate(), zone))
        assertEquals(LocalDate.of(2026, 10, 8), TaskRules.displayDate(facts(TaskDue.OnDay(LocalDate.of(2026, 9, 1)), rule = daily), now.toLocalDate(), zone))
        // A one-off task keeps its date however late it is.
        assertEquals(LocalDate.of(2026, 9, 1), TaskRules.displayDate(facts(TaskDue.OnDay(LocalDate.of(2026, 9, 1))), now.toLocalDate(), zone))
    }

    @Test
    public fun nextInstanceKeepsTheTimeOfDay() {
        val next = TaskRules.nextInstanceDue(at("2026-10-08T18:00"), RecurrenceRule.Daily(), LocalDate.of(2026, 10, 8), RepeatMode.FROM_DUE_DATE)
        assertEquals(at("2026-10-09T18:00"), next)
    }

    @Test
    public fun monthlyOnThe31stNextInstanceFallsOnTheLastDay() {
        val next = TaskRules.nextInstanceDue(TaskDue.OnDay(LocalDate.of(2026, 1, 31)), RecurrenceRule.Monthly(31), LocalDate.of(2026, 1, 31), RepeatMode.FROM_DUE_DATE)
        assertEquals(TaskDue.OnDay(LocalDate.of(2026, 2, 28)), next)
        // And from 28 Feb it goes back to the 31st, because the day lives in the rule.
        val after = TaskRules.nextInstanceDue(TaskDue.OnDay(LocalDate.of(2026, 2, 28)), RecurrenceRule.Monthly(31), LocalDate.of(2026, 2, 28), RepeatMode.FROM_DUE_DATE)
        assertEquals(TaskDue.OnDay(LocalDate.of(2026, 3, 31)), after)
    }

    @Test
    public fun completedLateOrFromCompletion() {
        val late = TaskRules.nextInstanceDue(TaskDue.OnDay(LocalDate.of(2026, 10, 1)), RecurrenceRule.Daily(), LocalDate.of(2026, 10, 8), RepeatMode.FROM_DUE_DATE)
        assertEquals(TaskDue.OnDay(LocalDate.of(2026, 10, 9)), late)
        val fromDone = TaskRules.nextInstanceDue(TaskDue.OnDay(LocalDate.of(2026, 10, 1)), RecurrenceRule.Weekly(setOf(java.time.DayOfWeek.MONDAY)), LocalDate.of(2026, 10, 8), RepeatMode.FROM_COMPLETION)
        assertEquals(TaskDue.OnDay(LocalDate.of(2026, 10, 15)), fromDone)
    }

    @Test
    public fun oneOffOrUndatedTasksHaveNoNextInstance() {
        assertNull(TaskRules.nextInstanceDue(at("2026-10-08T18:00"), null, LocalDate.of(2026, 10, 8), RepeatMode.FROM_DUE_DATE))
        assertNull(TaskRules.nextInstanceDue(TaskDue.None, RecurrenceRule.Daily(), LocalDate.of(2026, 10, 8), RepeatMode.FROM_DUE_DATE))
    }

    @Test
    public fun defaultReminders() {
        assertEquals(
            listOf(ReminderPlan(1_440, Importance.NORMAL), ReminderPlan(0, Importance.FULL_SCREEN)),
            TaskRules.defaultReminders(at("2026-10-09T17:00")),
        )
        assertEquals(listOf(ReminderPlan(0, Importance.NORMAL)), TaskRules.defaultReminders(TaskDue.OnDay(LocalDate.of(2026, 10, 9))))
        assertTrue(TaskRules.defaultReminders(TaskDue.None).isEmpty())
    }

    @Test
    public fun quickAddMapping() {
        val task = QuickAddParser.parse("report 17:30 #work @projectx !high", now).toQuickAddTask(zone)
        assertEquals("report #work", task.title)
        assertEquals(at("2026-10-09T17:30"), task.due)
        assertEquals(Priority.HIGH, task.priority)
        assertEquals("projectx", task.projectName)
        assertEquals(2, task.reminders.size)
    }

    @Test
    public fun quickAddWithoutDateGoesToTheInboxWithNoReminders() {
        val task = QuickAddParser.parse("anniversary gift", now).toQuickAddTask(zone)
        assertEquals(TaskDue.None, task.due)
        assertTrue(task.reminders.isEmpty())
    }

    @Test
    public fun quickAddDateOnly() {
        assertEquals(TaskDue.OnDay(LocalDate.of(2026, 10, 12)), QuickAddParser.parse("dentist 12/10", now).toQuickAddTask(zone).due)
    }

    @Test(expected = IllegalArgumentException::class)
    public fun openAmPmQuestionCannotBeSaved() {
        QuickAddParser.parse("meeting at 5", now).toQuickAddTask(zone)
    }
}
