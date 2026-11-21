package com.sanjith.betterlife.data.silent

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.domain.silent.SilentReminder
import com.sanjith.betterlife.domain.silent.VibrationPattern
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/** Silent reminders on real SQLite (owner's feature, 2026-11-21): save, switch, delete and Undo, each re-planning the alarm. */
@RunWith(AndroidJUnit4::class)
public class SilentReminderRepositoryTest {

    private lateinit var db: BetterLifeDatabase
    private lateinit var silent: SilentReminderRepository
    private var replans = 0
    private var idCounter = 0

    @Before
    public fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BetterLifeDatabase::class.java).build()
        silent = SilentReminderRepository(db, { 1_000L }, { "00000000-0000-4000-8000-%012d".format(++idCounter) }) { replans++ }
    }

    @After
    public fun tearDown() {
        db.close()
    }

    private fun draft() = SilentReminder("", " Mewing ", listOf(LocalTime.of(18, 30), LocalTime.of(10, 0)), VibrationPattern.HEARTBEAT, 30, enabled = true)

    @Test
    public fun savingKeepsTimesSortedAndTheNameTrimmedAndReplans() = runTest {
        val id = silent.save(draft())

        val saved = silent.get(id)
        assertEquals("Mewing", saved?.name)
        assertEquals(listOf(LocalTime.of(10, 0), LocalTime.of(18, 30)), saved?.times)
        assertEquals(VibrationPattern.HEARTBEAT, saved?.pattern)
        assertEquals(30, saved?.seconds)
        assertEquals(1, replans)
    }

    @Test
    public fun editingUpdatesTheSameRow() = runTest {
        val id = silent.save(draft())
        silent.save(silent.get(id)!!.copy(times = listOf(LocalTime.of(7, 0)), seconds = 10))

        val all = silent.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(listOf(LocalTime.of(7, 0)), all.single().times)
        assertEquals(10, all.single().seconds)
    }

    @Test
    public fun theSwitchTurnsItOffAndOn() = runTest {
        val id = silent.save(draft())
        silent.setEnabled(id, false)
        assertFalse(silent.get(id)!!.enabled)
        silent.setEnabled(id, true)
        assertTrue(silent.get(id)!!.enabled)
        assertEquals(3, replans)
    }

    @Test
    public fun deleteHidesItAndUndoBringsItBack() = runTest {
        val id = silent.save(draft())

        silent.delete(id)
        assertNull(silent.get(id))
        assertTrue(silent.active().isEmpty())

        silent.restore(id)
        assertEquals("Mewing", silent.get(id)?.name)
        assertEquals(3, replans)
    }
}
