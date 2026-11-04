package com.sanjith.betterlife.reminders.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.repository.EventDraft
import com.sanjith.betterlife.data.repository.EventRepository
import com.sanjith.betterlife.data.repository.ReminderDraft
import com.sanjith.betterlife.data.repository.TaskDraft
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.domain.delivery.ItemStatus
import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReminderState
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import com.sanjith.betterlife.domain.recurrence.RecurrenceRuleText
import com.sanjith.betterlife.domain.reminder.ItemTime
import com.sanjith.betterlife.domain.time.FixedClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
public class RoomReminderStoreTest {

    private lateinit var db: BetterLifeDatabase
    private lateinit var store: RoomReminderStore
    private lateinit var tasks: TaskRepository
    private lateinit var events: EventRepository
    private var idCounter = 0
    private val zone = ZoneId.of("Asia/Kolkata")
    private val due = Instant.parse("2026-10-09T11:30:00Z")

    @Before
    public fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BetterLifeDatabase::class.java).build()
        store = RoomReminderStore(db, FixedClock(Instant.parse("2026-10-09T10:00:00Z"), zone))
        val newId = { "id-${++idCounter}" }
        tasks = TaskRepository(db, { 1L }, newId)
        events = EventRepository(db, { 1L }, newId)
    }

    @After
    public fun tearDown() {
        db.close()
    }

    @Test
    public fun targetsJoinTasksAndEventsWithTheirTimesAndRules() = runTest {
        tasks.create(
            TaskDraft("Pills", dueAtMillis = due.toEpochMilli(), dueZoneId = zone, recurrenceRule = RecurrenceRuleText.encode(RecurrenceRule.Daily())),
            listOf(ReminderDraft(10, Importance.ALARM)),
        )
        events.create(
            EventDraft("Asha", type = EventType.BIRTHDAY, allDay = true, startDate = LocalDate.of(2026, 10, 20)),
            listOf(ReminderDraft(1_440)),
        )

        val targets = store.loadTargets().associateBy { it.offsetMinutes }

        val pills = targets.getValue(10)
        assertEquals(ItemTime.At(due, zone), pills.itemTime)
        assertEquals(RecurrenceRule.Daily(), pills.recurrence)
        assertEquals(Importance.ALARM, pills.importance)
        assertEquals(ItemTime.AllDay(LocalDate.of(2026, 10, 20)), targets.getValue(1_440).itemTime)
    }

    @Test
    public fun markFiredSucceedsOnlyOncePerFireTime() = runTest {
        tasks.create(TaskDraft("Pills", dueAtMillis = due.toEpochMilli(), dueZoneId = zone), listOf(ReminderDraft(0)))
        val id = store.loadTargets().single().reminderId

        assertTrue(store.markFired(id, due, ReminderState.FIRED))
        assertFalse(store.markFired(id, due, ReminderState.FIRED))
        // A different fire time (the next occurrence) is a new delivery.
        assertTrue(store.markFired(id, due.plusSeconds(86_400), ReminderState.SCHEDULED))
    }

    @Test
    public fun deletedTaskMakesItsReminderUnscheduleAndReportDeleted() = runTest {
        val taskId = tasks.create(TaskDraft("Pills", dueAtMillis = due.toEpochMilli(), dueZoneId = zone), listOf(ReminderDraft(0)))
        val reminderId = store.loadTargets().single().reminderId
        store.setScheduledFireAt(reminderId, due)

        tasks.softDelete(taskId)

        assertEquals(listOf(reminderId), store.idsToUnschedule())
        assertEquals(ItemStatus.DELETED, store.fireContext(reminderId)?.itemStatus)
        assertTrue(store.loadTargets().isEmpty())
    }

    @Test
    public fun popupDoneUsesTheInjectedCompletionSoRepeatsContinue() = runTest {
        val completed = mutableListOf<String>()
        val withHook = RoomReminderStore(db, FixedClock(Instant.parse("2026-10-09T10:00:00Z"), zone)) { completed += it }
        val taskId = tasks.create(TaskDraft("Pills", dueAtMillis = due.toEpochMilli(), dueZoneId = zone), listOf(ReminderDraft(0)))
        val reminderId = withHook.loadTargets().single().reminderId

        withHook.completeTask(reminderId)

        assertEquals(listOf(taskId), completed)
    }

    @Test
    public fun scheduleColumnsDoNotMarkTheRowForSync() = runTest {
        tasks.create(TaskDraft("Pills", dueAtMillis = due.toEpochMilli(), dueZoneId = zone), listOf(ReminderDraft(0)))
        val reminderId = store.loadTargets().single().reminderId
        db.openHelper.writableDatabase.execSQL("UPDATE reminders SET syncState = 'CLEAN'")

        store.setScheduledFireAt(reminderId, due)
        store.markFired(reminderId, due, ReminderState.FIRED)

        db.openHelper.readableDatabase.query("SELECT syncState FROM reminders").use { c ->
            c.moveToFirst()
            assertEquals("CLEAN", c.getString(0))
        }
    }
}
