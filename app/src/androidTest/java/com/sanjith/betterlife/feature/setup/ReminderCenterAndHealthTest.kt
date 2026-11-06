package com.sanjith.betterlife.feature.setup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.repository.EventRepository
import com.sanjith.betterlife.data.repository.ReminderDraft
import com.sanjith.betterlife.data.repository.ReminderLogRepository
import com.sanjith.betterlife.data.repository.StandaloneDraft
import com.sanjith.betterlife.data.repository.StandaloneReminderRepository
import com.sanjith.betterlife.data.repository.TaskDraft
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.data.setup.HealthStore
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.model.ReminderLogStatus
import com.sanjith.betterlife.domain.time.FixedClock
import com.sanjith.betterlife.feature.events.EventService
import com.sanjith.betterlife.reminders.engine.RoomReminderStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Reminder Center query and 60-day purge, and the Reminder Health test reminders, on real SQLite. */
@RunWith(AndroidJUnit4::class)
public class ReminderCenterAndHealthTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: BetterLifeDatabase
    private lateinit var logs: ReminderLogRepository
    private lateinit var tasks: TaskRepository
    private lateinit var standalone: StandaloneReminderRepository
    private lateinit var storeScope: CoroutineScope
    private lateinit var storeFile: File
    private var idCounter = 0
    private val now = Instant.parse("2026-11-06T10:00:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")

    @Before
    public fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, BetterLifeDatabase::class.java).build()
        val newId = { "id-${++idCounter}" }
        logs = ReminderLogRepository(db, { now.toEpochMilli() }, newId)
        tasks = TaskRepository(db, { 1L }, newId)
        standalone = StandaloneReminderRepository(db, { 1L }, newId)
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        storeFile = File(context.cacheDir, "health-test-${System.nanoTime()}.preferences_pb")
    }

    @After
    public fun tearDown() {
        storeScope.cancel()
        storeFile.delete()
        db.close()
    }

    private suspend fun reminderOf(type: ReminderItemType, itemId: String): String =
        db.reminderDao().getAllForItemIncludingDeleted(type, itemId).first().id

    @Test
    public fun centerRowsCarryTheirItemTitleEvenAfterTheItemIsDeleted() = runTest {
        val taskId = tasks.create(TaskDraft("Pay the bill", dueAtMillis = now.toEpochMilli(), dueZoneId = zone), listOf(ReminderDraft(0)))
        val remindId = standalone.create(StandaloneDraft("Call the bank", now.toEpochMilli(), zone), listOf(ReminderDraft(0)))
        val taskReminder = reminderOf(ReminderItemType.TASK, taskId)
        val remindReminder = reminderOf(ReminderItemType.STANDALONE, remindId)
        logs.append(taskReminder, ReminderLogStatus.FIRED, Importance.FULL_SCREEN, firedAt = now.toEpochMilli())
        logs.append(remindReminder, ReminderLogStatus.SNOOZED, Importance.NORMAL, firedAt = now.plusSeconds(60).toEpochMilli())
        logs.append("gone", ReminderLogStatus.MISSED, Importance.NORMAL, firedAt = now.plusSeconds(120).toEpochMilli())
        tasks.softDelete(taskId)

        val rows = logs.observeCenter(0, 100).first()

        assertEquals(listOf("gone", remindReminder, taskReminder), rows.map { it.log.reminderId })
        assertNull(rows[0].itemTitle)
        assertEquals("Call the bank", rows[1].itemTitle)
        assertEquals(ReminderItemType.STANDALONE, rows[1].itemType)
        assertEquals("Pay the bill", rows[2].itemTitle)
        assertEquals(taskId, rows[2].itemId)
        assertTrue(rows[2].itemDeletedAt != null)
    }

    @Test
    public fun purgeRemovesOnlyRowsOlderThanSixtyDays() = runTest {
        val sixtyDays = Duration.ofDays(60)
        logs.append("r", ReminderLogStatus.FIRED, Importance.NORMAL, firedAt = now.minus(sixtyDays).minusSeconds(1).toEpochMilli())
        logs.append("r", ReminderLogStatus.FIRED, Importance.NORMAL, firedAt = now.minus(sixtyDays).toEpochMilli())
        logs.append("r", ReminderLogStatus.FIRED, Importance.NORMAL, firedAt = now.toEpochMilli())

        assertEquals(1, logs.purgeOld(now.toEpochMilli()))
        assertEquals(2, logs.observeCount().first())
        assertEquals(0, logs.purgeOld(now.toEpochMilli()))
    }

    @Test
    public fun testRemindersAreRealFullScreenRemindersAndReplaceTheLastOne() = runTest {
        val store = HealthStore(PreferenceDataStoreFactory.create(scope = storeScope) { storeFile })
        var reschedules = 0
        val service = EventService(EventRepository(db, { 1L }, { "e-${++idCounter}" }), standalone) { reschedules++ }
        val tests = HealthTests(service, standalone, store, FixedClock(now, zone))
        val reminderStore = RoomReminderStore(db, FixedClock(now, zone))

        val first = tests.startQuick("Test reminder")
        assertEquals(now.plusSeconds(10).toEpochMilli(), first.dueMillis)
        val target = reminderStore.loadTargets().single()
        assertEquals(first.reminderId, target.reminderId)
        assertEquals(Importance.FULL_SCREEN, target.importance)
        assertEquals(0, target.offsetMinutes)
        assertEquals("Test reminder", reminderStore.fireContext(first.reminderId)?.title)

        val second = tests.startQuick("Test reminder")
        assertEquals(listOf(second.reminderId), reminderStore.loadTargets().map { it.reminderId })
        assertTrue(standalone.getAnyById(first.itemId)?.deletedAt != null)

        val closed = tests.startClosedApp("Test after closing the app")
        assertEquals(now.plusSeconds(120).toEpochMilli(), closed.dueMillis)
        assertEquals(closed, store.closedAppTest.first())
        assertEquals(2, reminderStore.loadTargets().size)
        assertTrue(reschedules >= 3)
    }
}
