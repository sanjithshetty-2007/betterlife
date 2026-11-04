package com.sanjith.betterlife.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.entity.SyncState
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReminderItemType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
public class TaskRepositoryTest {

    private lateinit var db: BetterLifeDatabase
    private var now = 1_800_000_000_000L
    private var idCounter = 0
    private val nextId: () -> String = { "id-${++idCounter}" }
    private lateinit var tasks: TaskRepository

    @Before
    public fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BetterLifeDatabase::class.java)
            .build()
        tasks = TaskRepository(db, { now }, nextId)
    }

    @After
    public fun tearDown() {
        db.close()
    }

    private fun draft(title: String = "Call mom") =
        TaskDraft(title = title, dueAtMillis = now + 3_600_000, dueZoneId = ZoneId.of("Asia/Kolkata"))

    @Test
    public fun createdTaskIsVisibleAndDirty() = runTest {
        val id = tasks.create(draft(), listOf(ReminderDraft(0), ReminderDraft(60, Importance.FULL_SCREEN)))

        val active = tasks.observeActive().first()
        assertEquals(listOf(id), active.map { it.id })
        assertEquals(SyncState.DIRTY, active.single().syncState)
        assertEquals(2, db.reminderDao().observeForItem(ReminderItemType.TASK, id).first().size)
    }

    @Test
    public fun softDeletedTaskAndItsRemindersAreExcluded() = runTest {
        val keep = tasks.create(draft("Keep"))
        val gone = tasks.create(draft("Gone"), listOf(ReminderDraft(10)))
        now += 1_000

        tasks.softDelete(gone)

        assertEquals(listOf(keep), tasks.observeActive().first().map { it.id })
        assertNull(tasks.observeById(gone).first())
        assertTrue(db.reminderDao().observeForItem(ReminderItemType.TASK, gone).first().isEmpty())
        assertEquals(now, db.taskDao().getAnyById(gone)?.deletedAt)
    }

    @Test
    public fun restoreBringsBackTheTaskAndOnlyTheRemindersDeletedWithIt() = runTest {
        val id = tasks.create(draft(), listOf(ReminderDraft(0), ReminderDraft(30)))
        val earlier = db.reminderDao().observeForItem(ReminderItemType.TASK, id).first().first()
        // One reminder was removed on its own earlier; restoring the task must not bring it back.
        now += 1_000
        db.openHelper.writableDatabase.execSQL(
            "UPDATE reminders SET deletedAt = ? WHERE id = ?",
            arrayOf<Any>(now, earlier.id),
        )
        now += 1_000
        tasks.softDelete(id)
        now += 1_000

        tasks.restore(id)

        assertNotNull(tasks.observeById(id).first())
        val restored = db.reminderDao().observeForItem(ReminderItemType.TASK, id).first()
        assertEquals(1, restored.size)
        assertTrue(restored.none { it.id == earlier.id })
    }

    @Test
    public fun failedReminderInsertRollsBackTheWholeCreate() = runTest {
        // Every id is the same, so the second reminder collides and the transaction must roll back.
        val clashing = TaskRepository(db, { now }, { "same-id" })

        try {
            clashing.create(draft(), listOf(ReminderDraft(0), ReminderDraft(10)))
            fail("Expected a constraint failure")
        } catch (expected: SQLiteConstraintException) {
            // Expected.
        }

        assertTrue(tasks.observeActive().first().isEmpty())
        assertEquals(0, db.reminderDao().observeActiveCount().first())
    }

    @Test
    public fun unknownProjectIsRejectedAndNothingIsWritten() = runTest {
        try {
            tasks.create(draft().copy(projectId = "missing"))
            fail("Expected the unknown project to be rejected")
        } catch (expected: IllegalArgumentException) {
            // Expected.
        }
        assertTrue(tasks.observeActive().first().isEmpty())
    }

    @Test
    public fun unknownParentTaskIsRejected() = runTest {
        try {
            tasks.create(draft().copy(parentTaskId = "missing"))
            fail("Expected the unknown parent task to be rejected")
        } catch (expected: IllegalArgumentException) {
            // Expected.
        }
        assertTrue(tasks.observeActive().first().isEmpty())
    }

    @Test
    public fun deletedParentTaskIsRejected() = runTest {
        val parent = tasks.create(draft("Parent"))
        tasks.softDelete(parent)
        try {
            tasks.create(draft("Child").copy(parentTaskId = parent))
            fail("Expected the deleted parent task to be rejected")
        } catch (expected: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    public fun existingParentTaskIsAccepted() = runTest {
        val parent = tasks.create(draft("Parent"))
        val child = tasks.create(draft("Child").copy(parentTaskId = parent))
        assertEquals(parent, db.taskDao().getAnyById(child)?.parentTaskId)
    }

    @Test
    public fun updateMarksDirtyAndBumpsUpdatedAt() = runTest {
        val id = tasks.create(draft())
        val created = db.taskDao().getAnyById(id)!!
        db.taskDao().update(created.copy(syncState = SyncState.CLEAN))
        now += 5_000

        tasks.update(created.copy(title = "Call mom tonight"))

        val updated = db.taskDao().getAnyById(id)!!
        assertEquals("Call mom tonight", updated.title)
        assertEquals(now, updated.updatedAt)
        assertEquals(SyncState.DIRTY, updated.syncState)
    }
}
