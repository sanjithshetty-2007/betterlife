package com.sanjith.betterlife.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.entity.SyncState
import com.sanjith.betterlife.data.repository.ReminderDraft
import com.sanjith.betterlife.data.repository.TaskDraft
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.domain.model.ReminderState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

/** The Room side of sync on real SQLite (Prompt S2). */
@RunWith(AndroidJUnit4::class)
public class RoomSyncStoreTest {

    private lateinit var db: BetterLifeDatabase
    private lateinit var store: RoomSyncStore
    private lateinit var tasks: TaskRepository
    private var now = 1_000L
    private var idCounter = 0

    @Before
    public fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BetterLifeDatabase::class.java).build()
        store = RoomSyncStore(db)
        tasks = TaskRepository(db, { now }, { "00000000-0000-4000-8000-%012d".format(++idCounter) })
    }

    @After
    public fun tearDown() {
        db.close()
    }

    @Test
    public fun dirtyRowsAreFoundButDebugSeedRowsNeverLeaveThePhone() = runTest {
        tasks.create(TaskDraft("Real", dueAtMillis = 5_000L, dueZoneId = ZoneId.of("Asia/Kolkata")), listOf(ReminderDraft(0)))
        tasks.create(TaskDraft("Seed", category = "debug-seed", dueAtMillis = 5_000L, dueZoneId = ZoneId.of("Asia/Kolkata")), listOf(ReminderDraft(0)))

        assertEquals(listOf("Real"), store.dirty(SyncTables.TASKS, 100).map { (it["title"] as JsonPrimitive).content })
        assertEquals(1, store.dirty(SyncTables.REMINDERS, 100).size)
        assertEquals(2, db.syncDao().observePendingCount().first())
    }

    @Test
    public fun markCleanOnlyWhenTheRowWasNotEditedMeanwhile() = runTest {
        val id = tasks.create(TaskDraft("Pay bill"))
        val pushed = store.dirty(SyncTables.TASKS, 1).single()
        now = 2_000L
        tasks.update(tasks.getAnyById(id)!!.copy(title = "Pay the bill"))

        assertFalse(store.markClean(SyncTables.TASKS, id, EntityJson.meta(pushed).updatedAt))
        assertEquals(SyncState.DIRTY, tasks.getAnyById(id)!!.syncState)
        assertTrue(store.markClean(SyncTables.TASKS, id, 2_000L))
        assertEquals(0, db.syncDao().observePendingCount().first())
    }

    @Test
    public fun pulledRowsAreCleanAndAPulledReminderKeepsItsFireState() = runTest {
        val id = tasks.create(TaskDraft("Pay bill", dueAtMillis = 5_000L, dueZoneId = ZoneId.of("Asia/Kolkata")), listOf(ReminderDraft(0)))
        val reminder = db.reminderDao().getSchedulable().single()
        db.reminderDao().markFired(reminder.id, 5_000L, ReminderState.FIRED)

        val serverTask = JsonObject(EntityJson.task(tasks.getAnyById(id)!!.copy(title = "From the cloud", updatedAt = 9_000L)) + stamp())
        val serverReminder = JsonObject(EntityJson.reminder(reminder.copy(offsetMinutes = 30, updatedAt = 9_000L)) + stamp())
        store.applyRemote(SyncTables.TASKS, listOf(serverTask))
        store.applyRemote(SyncTables.REMINDERS, listOf(serverReminder))

        val task = tasks.getAnyById(id)!!
        assertEquals("From the cloud", task.title)
        assertEquals(SyncState.CLEAN, task.syncState)
        val merged = db.reminderDao().getAnyById(reminder.id)!!
        assertEquals(30, merged.offsetMinutes)
        assertEquals(5_000L, merged.lastFiredFireAt)
        assertEquals(ReminderState.FIRED, merged.state)
        assertEquals(mapOf(id to com.sanjith.betterlife.domain.sync.RowMeta(id, 9_000L, null)), store.metas(SyncTables.TASKS, listOf(id)))
    }

    @Test
    public fun cursorsAndTheFullResetForANewAccount() = runTest {
        tasks.create(TaskDraft("A"))
        store.dirty(SyncTables.TASKS, 10).forEach { store.markClean(SyncTables.TASKS, EntityJson.meta(it).id, EntityJson.meta(it).updatedAt) }
        store.setCursor(SyncTables.TASKS, 1_793_000_000_000_001L)
        assertEquals(1_793_000_000_000_001L, store.cursor(SyncTables.TASKS))

        store.markEverythingDirtyAndResetCursors()

        assertEquals(1, store.dirty(SyncTables.TASKS, 10).size)
        assertEquals(null, store.cursor(SyncTables.TASKS))
    }

    private fun stamp() = mapOf("server_updated_at" to JsonPrimitive("2026-11-06T11:07:33.000001+00:00"))
}
