package com.sanjith.betterlife.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.protein.ProteinRepository
import com.sanjith.betterlife.data.protein.ProteinSaveResult
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
import java.time.LocalDate

/** Protein days on real SQLite (owner's feature, 2026-11-21): one row per date, today and yesterday only. */
@RunWith(AndroidJUnit4::class)
public class ProteinRepositoryTest {

    private lateinit var db: BetterLifeDatabase
    private lateinit var protein: ProteinRepository
    private var now = 1_000L
    private var idCounter = 0
    private val today = LocalDate.parse("2026-11-21")

    @Before
    public fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BetterLifeDatabase::class.java).build()
        protein = ProteinRepository(db, { now }, { "00000000-0000-4000-8000-%012d".format(++idCounter) })
    }

    @After
    public fun tearDown() {
        db.close()
    }

    @Test
    public fun savingTheSameDayTwiceUpdatesTheOneRow() = runTest {
        assertEquals(ProteinSaveResult.SAVED, protein.save(today, 60, today, goal = 120))
        now = 2_000L
        assertEquals(ProteinSaveResult.SAVED, protein.save(today, 130, today, goal = 120))

        val days = protein.observeDays().first()
        assertEquals(1, days.size)
        assertEquals(130, days.single().grams)
        assertTrue(days.single().met)
    }

    @Test
    public fun yesterdayIsSavedOnItsOwnDateAndOlderDaysAreRefused() = runTest {
        assertEquals(ProteinSaveResult.SAVED, protein.save(today.minusDays(1), 90, today, goal = 120))
        assertEquals(ProteinSaveResult.NOT_EDITABLE, protein.save(today.minusDays(2), 90, today, goal = 120))

        assertEquals(90, protein.day(today.minusDays(1))?.grams)
        assertNull(protein.day(today))
        assertNull(protein.day(today.minusDays(2)))
    }

    @Test
    public fun yesterdayKeepsItsOwnTargetWhenCorrected() = runTest {
        protein.save(today.minusDays(1), 90, today.minusDays(1), goal = 100)
        // The goal was raised to 150 today; correcting yesterday keeps yesterday's 100.
        protein.save(today.minusDays(1), 110, today, goal = 150)

        val yesterday = protein.day(today.minusDays(1))
        assertEquals(100, yesterday?.targetGrams)
        assertTrue(yesterday?.met == true)
    }

    @Test
    public fun aNewGoalAppliesToTodayOnly() = runTest {
        protein.save(today.minusDays(1), 110, today, goal = 100)
        protein.save(today, 110, today, goal = 100)

        protein.retarget(today, 150)

        assertEquals(150, protein.day(today)?.targetGrams)
        assertFalse(protein.day(today)?.met == true)
        assertEquals(100, protein.day(today.minusDays(1))?.targetGrams)
    }

    @Test
    public fun noGoalAndBadAmountsAreRefused() = runTest {
        assertEquals(ProteinSaveResult.NO_GOAL, protein.save(today, 50, today, goal = null))
        assertEquals(ProteinSaveResult.INVALID, protein.save(today, 1_000, today, goal = 120))
        assertEquals(ProteinSaveResult.INVALID, protein.save(today, -1, today, goal = 120))
        assertFalse(protein.isLogged(today))
    }
}
