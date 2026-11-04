package com.sanjith.betterlife.feature.tasks

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.repository.ProjectRepository
import com.sanjith.betterlife.data.repository.ReminderDraft
import com.sanjith.betterlife.data.repository.TaskDraft
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.data.repository.due
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.parser.QuickAddParser
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import com.sanjith.betterlife.domain.recurrence.RecurrenceRuleText
import com.sanjith.betterlife.domain.task.TaskDue
import com.sanjith.betterlife.domain.time.FixedClock
import com.sanjith.betterlife.domain.time.zonedNow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
public class TaskServiceTest {

    private val clock = FixedClock.of("2026-10-08T18:00", "Asia/Kolkata")
    private lateinit var db: BetterLifeDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var projects: ProjectRepository
    private lateinit var service: TaskService
    private var reschedules = 0
    private var idCounter = 0

    @Before
    public fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BetterLifeDatabase::class.java).build()
        val newId = { "id-${++idCounter}" }
        tasks = TaskRepository(db, { 1_000L }, newId)
        projects = ProjectRepository(db, { 1_000L }, newId)
        service = TaskService(tasks, projects, clock) { reschedules++ }
    }

    @After
    public fun tearDown() {
        db.close()
    }

    private suspend fun remindersOf(id: String) = db.reminderDao().observeForItem(ReminderItemType.TASK, id).first()

    @Test
    public fun quickAddSavesDueProjectRemindersAndReschedules() = runTest {
        val id = service.quickAdd(QuickAddParser.parse("call mom tomorrow 5pm @family", clock.zonedNow()))

        val task = tasks.getAnyById(id)!!
        assertEquals("call mom", task.title)
        assertEquals(LocalDateTime.parse("2026-10-09T17:00").atZone(clock.zone).toInstant().toEpochMilli(), task.dueAtMillis)
        assertNotNull(task.projectId)
        assertEquals(setOf(1_440 to Importance.NORMAL, 0 to Importance.FULL_SCREEN), remindersOf(id).map { it.offsetMinutes to it.importance }.toSet())
        assertEquals(1, reschedules)
    }

    @Test
    public fun quickAddReusesAnExistingProjectWhateverTheCase() = runTest {
        val existing = projects.create("Family")
        val id = service.quickAdd(QuickAddParser.parse("dinner @family", clock.zonedNow()))
        assertEquals(existing, tasks.getAnyById(id)!!.projectId)
    }

    @Test
    public fun completingARepeatingTaskCreatesTheNextInstanceWithItsReminders() = runTest {
        val id = service.create(
            TaskDraft("Pills", dueDate = LocalDate.of(2026, 10, 8), recurrenceRule = RecurrenceRuleText.encode(RecurrenceRule.Daily())),
            listOf(ReminderDraft(0, Importance.ALARM)),
        )

        val result = service.complete(id)

        assertNotNull(tasks.getAnyById(id)!!.completedAt)
        val next = tasks.getAnyById(result.nextInstanceId!!)!!
        assertEquals(TaskDue.OnDay(LocalDate.of(2026, 10, 9)), next.due)
        assertNull(next.completedAt)
        assertEquals(listOf(Importance.ALARM), remindersOf(next.id).map { it.importance })
    }

    @Test
    public fun undoCompleteReopensTheTaskAndRemovesTheNextInstance() = runTest {
        val id = service.create(
            TaskDraft("Pills", dueDate = LocalDate.of(2026, 10, 8), recurrenceRule = RecurrenceRuleText.encode(RecurrenceRule.Daily())),
            listOf(ReminderDraft(0)),
        )
        val result = service.complete(id)

        service.undoComplete(result)

        assertNull(tasks.getAnyById(id)!!.completedAt)
        assertNotNull(tasks.getAnyById(result.nextInstanceId!!)!!.deletedAt)
        assertTrue(remindersOf(result.nextInstanceId!!).isEmpty())
        assertEquals(1, tasks.observeActive().first().count { it.title == "Pills" && it.completedAt == null })
    }

    @Test
    public fun oneOffTaskHasNoNextInstance() = runTest {
        val id = service.create(TaskDraft("Once", dueDate = LocalDate.of(2026, 10, 8)), emptyList())
        assertNull(service.complete(id).nextInstanceId)
    }

    @Test
    public fun deleteAndUndoKeepRemindersAndRescheduleEachTime() = runTest {
        val id = service.create(TaskDraft("Call", dueDate = LocalDate.of(2026, 10, 9)), listOf(ReminderDraft(0)))
        val before = reschedules

        service.delete(id)
        assertTrue(remindersOf(id).isEmpty())
        service.undoDelete(id)

        assertEquals(1, remindersOf(id).size)
        assertNull(tasks.getAnyById(id)!!.deletedAt)
        assertEquals(before + 2, reschedules)
    }

    @Test
    public fun moveToTomorrowKeepsTimeAndReminders() = runTest {
        val due = LocalDateTime.parse("2026-10-08T21:00").atZone(clock.zone).toInstant().toEpochMilli()
        val id = service.create(TaskDraft("Read", dueAtMillis = due, dueZoneId = clock.zone), listOf(ReminderDraft(0), ReminderDraft(30)))

        service.moveToTomorrow(id)

        assertEquals(LocalDateTime.parse("2026-10-09T21:00").atZone(clock.zone).toInstant().toEpochMilli(), tasks.getAnyById(id)!!.dueAtMillis)
        assertEquals(setOf(0, 30), remindersOf(id).map { it.offsetMinutes }.toSet())
    }

    @Test
    public fun editorSaveReplacesReminders() = runTest {
        val id = service.create(TaskDraft("Call", dueDate = LocalDate.of(2026, 10, 9)), listOf(ReminderDraft(0), ReminderDraft(60)))
        val task = tasks.getAnyById(id)!!

        service.save(task.copy(title = "Call back"), listOf(ReminderDraft(1_440, Importance.FULL_SCREEN)))

        assertEquals("Call back", tasks.getAnyById(id)!!.title)
        assertEquals(listOf(1_440 to Importance.FULL_SCREEN), remindersOf(id).map { it.offsetMinutes to it.importance })
    }
}
