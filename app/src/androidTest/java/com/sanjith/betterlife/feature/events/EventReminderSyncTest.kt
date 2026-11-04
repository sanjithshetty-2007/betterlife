package com.sanjith.betterlife.feature.events

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.repository.EventDraft
import com.sanjith.betterlife.data.repository.EventRepository
import com.sanjith.betterlife.data.repository.ReminderDraft
import com.sanjith.betterlife.data.repository.StandaloneDraft
import com.sanjith.betterlife.data.repository.StandaloneReminderRepository
import com.sanjith.betterlife.domain.delivery.ItemStatus
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.reminder.ItemTime
import com.sanjith.betterlife.domain.time.FixedClock
import com.sanjith.betterlife.reminders.engine.RoomReminderStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

/**
 * Prompt 10: editing, rescheduling, completing or deleting an event or standalone reminder cancels
 * or re-creates its pending reminders. The engine cancels the alarm of every row
 * [RoomReminderStore.idsToUnschedule] returns, and schedules what [RoomReminderStore.loadTargets] returns.
 */
@RunWith(AndroidJUnit4::class)
public class EventReminderSyncTest {

    private lateinit var db: BetterLifeDatabase
    private lateinit var events: EventRepository
    private lateinit var standalone: StandaloneReminderRepository
    private lateinit var store: RoomReminderStore
    private lateinit var service: EventService
    private var reschedules = 0
    private var idCounter = 0
    private val zone = ZoneId.of("Asia/Kolkata")
    private val start = Instant.parse("2026-11-10T04:30:00Z")

    @Before
    public fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BetterLifeDatabase::class.java).build()
        val newId = { "id-${++idCounter}" }
        events = EventRepository(db, { 1L }, newId)
        standalone = StandaloneReminderRepository(db, { 2L }, newId)
        store = RoomReminderStore(db, FixedClock(Instant.parse("2026-11-04T10:00:00Z"), zone))
        service = EventService(events, standalone) { reschedules++ }
    }

    @After
    public fun tearDown() {
        db.close()
    }

    private suspend fun scheduleAll() {
        store.loadTargets().forEach { store.setScheduledFireAt(it.reminderId, start) }
    }

    @Test
    public fun editingAnEventsTimeReplacesItsRemindersAndCancelsTheOldAlarms() = runTest {
        val id = service.createEvent(
            EventDraft("Dentist", startAtMillis = start.toEpochMilli(), zoneId = zone),
            listOf(ReminderDraft(60), ReminderDraft(0, Importance.FULL_SCREEN)),
        )
        scheduleAll()
        val oldIds = store.loadTargets().map { it.reminderId }.toSet()

        val later = start.plusSeconds(7_200)
        val event = events.getAnyById(id)!!
        service.saveEvent(event.copy(startAtMillis = later.toEpochMilli()), listOf(ReminderDraft(30)))

        assertEquals(oldIds, store.idsToUnschedule().toSet())
        val target = store.loadTargets().single()
        assertEquals(30, target.offsetMinutes)
        assertEquals(ItemTime.At(later, zone), target.itemTime)
        assertEquals(2, reschedules)
        assertEquals(listOf(ReminderDraft(30)), events.activeReminders(id))
    }

    @Test
    public fun deletingAnEventCancelsEveryAlarmAndUndoBringsThemBack() = runTest {
        val id = service.createEvent(EventDraft("Dentist", startAtMillis = start.toEpochMilli(), zoneId = zone), listOf(ReminderDraft(60), ReminderDraft(0)))
        scheduleAll()

        service.deleteEvent(id)
        assertEquals(2, store.idsToUnschedule().size)
        assertTrue(store.loadTargets().isEmpty())

        service.undoDeleteEvent(id)
        assertEquals(2, store.loadTargets().size)
        assertEquals(3, reschedules)
    }

    @Test
    public fun standaloneReminderFiresWithItsOwnTitleAndTime() = runTest {
        val id = service.createReminder(StandaloneDraft("Call the bank", start.toEpochMilli(), zone), listOf(ReminderDraft(10), ReminderDraft(0)))

        val targets = store.loadTargets()
        assertEquals(2, targets.size)
        assertTrue(targets.all { it.itemTime == ItemTime.At(start, zone) && !it.itemDone })
        val context = store.fireContext(targets.first().reminderId)!!
        assertEquals("Call the bank", context.title)
        assertEquals(ItemStatus.ACTIVE, context.itemStatus)
        assertEquals(id, context.itemId)
    }

    @Test
    public fun popupDoneMarksAStandaloneReminderDoneSoItStops() = runTest {
        val id = service.createReminder(StandaloneDraft("Call the bank", start.toEpochMilli(), zone), listOf(ReminderDraft(0)))
        val reminderId = store.loadTargets().single().reminderId

        store.completeTask(reminderId)

        assertEquals(ItemStatus.COMPLETED, store.fireContext(reminderId)?.itemStatus)
        assertTrue(store.loadTargets().single().itemDone)
        assertTrue(standalone.observeActive().first().isEmpty())
        service.undoReminderDone(id)
        assertEquals(1, standalone.observeActive().first().size)
    }

    @Test
    public fun editingAndDeletingAStandaloneReminderKeepsAlarmsInStep() = runTest {
        val id = service.createReminder(StandaloneDraft("Call the bank", start.toEpochMilli(), zone), listOf(ReminderDraft(0)))
        scheduleAll()
        val item = standalone.getAnyById(id)!!

        service.saveReminder(item.copy(atMillis = start.plusSeconds(3_600).toEpochMilli()), listOf(ReminderDraft(5)))
        assertEquals(1, store.idsToUnschedule().size)
        assertEquals(ItemTime.At(start.plusSeconds(3_600), zone), store.loadTargets().single().itemTime)

        scheduleAll()
        service.deleteReminder(id)
        assertEquals(2, store.idsToUnschedule().size)
        assertTrue(store.loadTargets().isEmpty())
        assertEquals(ItemStatus.DELETED, store.fireContext(db.reminderDao().getAllForItemIncludingDeleted(
            com.sanjith.betterlife.domain.model.ReminderItemType.STANDALONE, id,
        ).first().id)?.itemStatus)
    }
}
