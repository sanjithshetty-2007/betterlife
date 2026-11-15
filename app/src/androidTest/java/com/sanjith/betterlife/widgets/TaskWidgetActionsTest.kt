package com.sanjith.betterlife.widgets

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.repository.ProjectRepository
import com.sanjith.betterlife.data.repository.ReminderDraft
import com.sanjith.betterlife.data.repository.TaskDraft
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.time.FixedClock
import com.sanjith.betterlife.feature.tasks.TaskService
import com.sanjith.betterlife.reminders.engine.RoomReminderStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/** The task widget and quick-add popup on real SQLite (Prompt 20): reminders stop and start exactly as in the app. */
@RunWith(AndroidJUnit4::class)
public class TaskWidgetActionsTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BetterLifeDatabase::class.java).build()
    private val clock = FixedClock.of("2026-11-20T10:00", "Asia/Kolkata")
    private val zone = ZoneId.of("Asia/Kolkata")
    private val ids = AtomicInteger()
    private val newId = { "00000000-0000-4000-8000-%012d".format(ids.incrementAndGet()) }
    private val tasks = TaskRepository(db, { 1_000L }, newId)
    private var reschedules = 0
    private val service = TaskService(tasks, ProjectRepository(db, { 1_000L }, newId), clock) { reschedules++ }
    private val actions = TaskWidgetActions(service)
    private val store = RoomReminderStore(db, clock)

    @After
    public fun tearDown() {
        db.close()
    }

    private fun taskWithReminder(): String = runBlocking {
        tasks.create(
            TaskDraft("Pay bill", dueAtMillis = clock.now().plusSeconds(HOURS_2).toEpochMilli(), dueZoneId = zone),
            listOf(ReminderDraft(0, Importance.FULL_SCREEN)),
        )
    }

    @Test
    public fun tickingOnTheWidgetStopsTheTasksReminders() {
        val id = taskWithReminder()
        assertEquals(listOf(false), runBlocking { store.loadTargets() }.map { it.itemDone })

        runBlocking { actions.complete(id) }

        assertNotNull(runBlocking { tasks.getAnyById(id) }!!.completedAt)
        // The planner skips a reminder whose item is done, so no alarm is set for it again.
        assertEquals(listOf(true), runBlocking { store.loadTargets() }.map { it.itemDone })
        assertEquals(1, reschedules)
    }

    @Test
    public fun deletingOnTheWidgetCancelsTheTasksReminders() {
        val id = taskWithReminder()
        runBlocking { actions.delete(id) }

        assertNotNull(runBlocking { tasks.getAnyById(id) }!!.deletedAt)
        assertTrue(runBlocking { store.loadTargets() }.isEmpty())
        assertEquals(1, reschedules)
    }

    @Test
    public fun savingFromTheQuickAddPopupCreatesTheTaskWithItsReminders() {
        val viewModel = QuickAddViewModel(service, clock)
        viewModel.onText("call mom tomorrow 5pm")
        val id = runBlocking { viewModel.save() }
        assertNotNull(id)
        val task = runBlocking { tasks.getAnyById(id!!) }!!
        assertEquals("call mom", task.title)
        assertEquals(clock.now().atZone(zone).toLocalDate().plusDays(1).atTime(17, 0).atZone(zone).toInstant().toEpochMilli(), task.dueAtMillis)
        // A timed task starts with the default reminders: the day before and at the time.
        assertEquals(setOf(24 * 60, 0), runBlocking { store.loadTargets() }.map { it.offsetMinutes }.toSet())
        assertEquals(1, reschedules)
        assertEquals(id, viewModel.saved.value)
    }

    @Test
    public fun anUnreadyLineSavesNothing() {
        val viewModel = QuickAddViewModel(service, clock)
        viewModel.onText("meeting at 5")
        // "at 5" asks AM or PM first, exactly as in the Tasks screen.
        assertEquals(null, runBlocking { viewModel.save() })
        assertEquals(0, reschedules)
    }

    private companion object {
        const val HOURS_2 = 7_200L
    }
}
