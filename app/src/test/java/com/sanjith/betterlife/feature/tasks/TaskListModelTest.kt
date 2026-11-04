package com.sanjith.betterlife.feature.tasks

import com.sanjith.betterlife.data.db.entity.ProjectEntity
import com.sanjith.betterlife.data.db.entity.SyncState
import com.sanjith.betterlife.data.db.entity.TaskEntity
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

public class TaskListModelTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.parse("2026-10-08T18:00").atZone(zone)
    private var created = 0L

    private fun task(
        id: String,
        dueAt: String? = null,
        dueDate: LocalDate? = null,
        priority: Priority = Priority.NONE,
        project: String? = null,
        parent: String? = null,
        done: Boolean = false,
    ) = TaskEntity(
        id = id, title = "Task $id", notes = null,
        dueAtMillis = dueAt?.let { LocalDateTime.parse(it).atZone(zone).toInstant().toEpochMilli() },
        dueZoneId = dueAt?.let { zone }, dueDate = dueDate, priority = priority, projectId = project, parentTaskId = parent,
        category = null, recurrenceRule = null, repeatFromCompletion = false, importance = Importance.NORMAL,
        completedAt = if (done) 1L else null, createdAt = ++created, updatedAt = 0, deletedAt = null,
        syncState = SyncState.CLEAN, serverUpdatedAt = null,
    )

    private val project = ProjectEntity("p1", "Work", null, false, 0, 0, 0, null, SyncState.CLEAN, null)

    @Test
    public fun tasksLandInTheRightTabs() {
        val tabs = buildTaskTabs(
            listOf(
                task("late", dueAt = "2026-10-08T09:00"),
                task("tonight", dueAt = "2026-10-08T21:00"),
                task("tomorrow", dueAt = "2026-10-09T09:00"),
                task("inbox"),
                task("projectOnly", project = "p1"),
                task("done", dueAt = "2026-10-08T21:00", done = true),
            ),
            listOf(project), emptySet(), now,
        )
        assertEquals(listOf("late"), tabs.overdue.map { it.id })
        assertEquals(listOf("tonight"), tabs.today.map { it.id })
        assertEquals(listOf("tomorrow"), tabs.upcoming.map { it.id })
        assertEquals(listOf("inbox"), tabs.inbox.map { it.id })
    }

    @Test
    public fun todayPutsTopTasksFirstThenPriorityThenTime() {
        val tabs = buildTaskTabs(
            listOf(
                task("low", dueAt = "2026-10-08T19:00", priority = Priority.LOW),
                task("high", dueAt = "2026-10-08T22:00", priority = Priority.HIGH),
                task("top", dueAt = "2026-10-08T23:00"),
                task("high-early", dueAt = "2026-10-08T20:00", priority = Priority.HIGH),
            ),
            emptyList(), setOf("top"), now,
        )
        assertEquals(listOf("top", "high-early", "high", "low"), tabs.today.map { it.id })
        assertTrue(tabs.today.first().isTop)
    }

    @Test
    public fun subtasksAreCountedOnTheParentNotListed() {
        val tabs = buildTaskTabs(
            listOf(task("parent"), task("s1", parent = "parent", done = true), task("s2", parent = "parent")),
            emptyList(), emptySet(), now,
        )
        assertEquals(listOf("parent"), tabs.inbox.map { it.id })
        assertEquals(1, tabs.inbox.single().subtasksDone)
        assertEquals(2, tabs.inbox.single().subtasksTotal)
    }

    @Test
    public fun inboxShowsNewestFirstAndProjectNamesShow() {
        val tabs = buildTaskTabs(
            listOf(task("old"), task("new"), task("work", dueAt = "2026-10-09T09:00", project = "p1")),
            listOf(project), emptySet(), now,
        )
        assertEquals(listOf("new", "old"), tabs.inbox.map { it.id })
        assertEquals("Work", tabs.upcoming.single().projectName)
    }

    // Editor model

    @Test
    public fun editorBuildsEachRepeatRuleFromTheDueDate() {
        val base = TaskEditorState(title = "x", dueDate = LocalDate.of(2026, 1, 31))
        assertNull(base.rule())
        assertEquals(RecurrenceRule.Daily(2), base.copy(repeat = RepeatKind.DAILY, interval = 2).rule())
        assertEquals(RecurrenceRule.Weekdays, base.copy(repeat = RepeatKind.WEEKDAYS).rule())
        assertEquals(RecurrenceRule.Weekly(setOf(DayOfWeek.SATURDAY)), base.copy(repeat = RepeatKind.WEEKLY).rule())
        assertEquals(RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)), base.copy(repeat = RepeatKind.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)).rule())
        assertEquals(RecurrenceRule.Monthly(31), base.copy(repeat = RepeatKind.MONTHLY).rule())
        assertEquals(RecurrenceRule.Yearly(java.time.Month.JANUARY, 31), base.copy(repeat = RepeatKind.YEARLY).rule())
    }

    @Test
    public fun editorFieldsRoundTripThroughARule() {
        listOf(RecurrenceRule.Daily(3), RecurrenceRule.Weekdays, RecurrenceRule.Weekly(setOf(DayOfWeek.TUESDAY), 2), RecurrenceRule.Monthly(31)).forEach { rule ->
            val (kind, interval, days) = TaskEditorState.repeatFields(rule)
            val state = TaskEditorState(title = "x", dueDate = LocalDate.of(2026, 1, 31), repeat = kind, interval = interval, weekdays = days)
            assertEquals(rule, state.rule())
        }
    }

    @Test
    public fun editorErrors() {
        assertEquals(listOf(EditorError.EMPTY_TITLE), TaskEditorState().errors())
        assertEquals(listOf(EditorError.REPEAT_NEEDS_DATE), TaskEditorState(title = "x", repeat = RepeatKind.DAILY).errors())
        assertTrue(TaskEditorState(title = "x").errors().isEmpty())
    }

    @Test
    public fun editorDueCombinesDateAndTime() {
        val s = TaskEditorState(title = "x", dueDate = LocalDate.of(2026, 10, 9), dueTime = LocalTime.of(17, 0))
        assertEquals(com.sanjith.betterlife.domain.task.TaskDue.At(LocalDateTime.parse("2026-10-09T17:00").atZone(zone).toInstant(), zone), s.due(zone))
        assertFalse(s.copy(dueTime = null).due(zone) is com.sanjith.betterlife.domain.task.TaskDue.At)
    }
}
