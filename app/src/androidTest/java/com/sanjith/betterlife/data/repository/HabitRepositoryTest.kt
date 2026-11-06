package com.sanjith.betterlife.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.entity.SyncState
import com.sanjith.betterlife.domain.habit.CelebrationKind
import com.sanjith.betterlife.domain.habit.DayState
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import com.sanjith.betterlife.domain.habit.MilestoneEvent
import com.sanjith.betterlife.domain.habit.PauseReason
import com.sanjith.betterlife.domain.habit.StarterHabits
import com.sanjith.betterlife.domain.habit.StreakCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Habit writes on real SQLite (Prompt 14): ticks, unticks, the milestone ledger and pause ranges. */
@RunWith(AndroidJUnit4::class)
public class HabitRepositoryTest {

    private lateinit var db: BetterLifeDatabase
    private lateinit var habits: HabitRepository
    private lateinit var pauses: PauseRangeRepository
    private var now = 1_000L
    private var idCounter = 0

    @Before
    public fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BetterLifeDatabase::class.java).build()
        val ids = { "00000000-0000-4000-8000-%012d".format(++idCounter) }
        habits = HabitRepository(db, { now }, ids)
        pauses = PauseRangeRepository(db, { now }, ids)
    }

    @After
    public fun tearDown() {
        db.close()
    }

    @Test
    public fun untickThenTickReusesTheOneRowForThatDay() = runTest {
        val id = habits.create(HabitDraft("Creatine", "#FF6B5E"), nov(1))
        habits.tick(id, nov(20), nov(20))
        val first = db.habitLogDao().getForDay(id, nov(20))!!
        assertEquals(SyncState.DIRTY, first.syncState)

        now = 2_000L
        habits.untick(id, nov(20), nov(20))
        assertEquals(2_000L, db.habitLogDao().getForDay(id, nov(20))!!.deletedAt)
        now = 3_000L
        habits.tick(id, nov(20), nov(20), HabitLogStatus.KEPT)

        val again = db.habitLogDao().getForDay(id, nov(20))!!
        assertEquals(first.id, again.id)
        assertNull(again.deletedAt)
        assertEquals(HabitLogStatus.KEPT, again.status)
        assertEquals(3_000L, again.updatedAt)
    }

    @Test
    public fun futureDaysAndUnknownOrDeletedHabitsWriteNothing() = runTest {
        val id = habits.create(HabitDraft("Meditation", "#7E8CFF"), nov(1))
        assertEquals(HabitEditResult.FutureDay, habits.tick(id, nov(21), nov(20)))
        assertEquals(HabitEditResult.FutureDay, habits.untick(id, nov(21), nov(20)))
        assertEquals(HabitEditResult.UnknownHabit, habits.tick("nope", nov(20), nov(20)))
        habits.softDelete(id)
        assertEquals(HabitEditResult.UnknownHabit, habits.tick(id, nov(20), nov(20)))
        assertEquals(emptyList<Any>(), db.habitLogDao().getActiveForHabit(id))
    }

    @Test
    public fun milestoneSevenFiresOnceEvenAfterUntickThenRetick() = runTest {
        val id = habits.create(HabitDraft("Skincare", "#FF8FB8"), nov(14))
        // Each day ticked on that day, like real use.
        (14..19).forEach { assertEquals(HabitEditResult.Saved(null), habits.tick(id, nov(it), nov(it))) }

        assertEquals(HabitEditResult.Saved(MilestoneEvent(7, CelebrationKind.FULL, nov(14))), habits.tick(id, nov(20), nov(20)))
        assertEquals(HabitEditResult.Saved(null), habits.untick(id, nov(20), nov(20)))
        assertEquals(HabitEditResult.Saved(null), habits.tick(id, nov(20), nov(20)))

        val ledger = db.milestoneLedgerDao().getForHabit(id)
        assertEquals(1, ledger.size)
        assertEquals(7, ledger.single().milestone)
        assertEquals(false, ledger.single().quiet)
    }

    @Test
    public fun aBackfillThatCreatesDaySevenIsQuiet() = runTest {
        val id = habits.create(HabitDraft("Haircare", "#5EC4D6"), nov(14))
        listOf(14, 15, 16, 18, 19, 20).forEach { habits.tick(id, nov(it), nov(it)) }

        val result = habits.tick(id, nov(17), today = nov(20))

        assertEquals(HabitEditResult.Saved(MilestoneEvent(7, CelebrationKind.QUIET, nov(14))), result)
        assertEquals(true, db.milestoneLedgerDao().getForHabit(id).single().quiet)
    }

    @Test
    public fun aTickBeforeTheStartDateMovesTheStartBack() = runTest {
        val id = habits.create(HabitDraft("Read", "#7BC67E"), nov(10))
        now = 5_000L
        habits.tick(id, nov(8), nov(20))
        val habit = habits.getAnyById(id)!!
        assertEquals(nov(8), habit.startDate)
        assertEquals(5_000L, habit.updatedAt)
        assertEquals(DayState.MISSED, habits.history(id)!!.state(nov(9), nov(20)))
    }

    @Test
    public fun aPauseRangeBridgesTheStreakAndCanBeEnded() = runTest {
        val id = habits.create(HabitDraft("Creatine", "#FF6B5E"), nov(1))
        listOf(13, 14, 15, 19, 20).forEach { habits.tick(id, nov(it), nov(it)) }
        val pause = pauses.start(nov(16), null, PauseReason.SICK)
        assertEquals(5, StreakCalculator.current(habits.history(id)!!, nov(20)))

        pauses.end(pause, nov(18))
        assertEquals(nov(18), db.pauseRangeDao().getAnyById(pause)!!.endDate)
        assertEquals(5, StreakCalculator.current(habits.history(id)!!, nov(20)))

        // Ending a pause before it began removes it.
        val later = pauses.start(nov(25), nov(27), PauseReason.VACATION)
        pauses.end(later, nov(24))
        assertNotNull(db.pauseRangeDao().getAnyById(later)!!.deletedAt)
    }

    @Test
    public fun startersAreAddedOnceAndADeletedStarterStaysDeleted() = runTest {
        val names = { key: com.sanjith.betterlife.domain.habit.StarterHabit.Key -> key.name }
        habits.insertStarters(StarterHabits.ALL, names, nov(6))
        assertEquals(listOf("CREATINE", "MEDITATION", "SKINCARE", "HAIRCARE"), habits.observeActive().first().map { it.name })
        assertEquals(true, habits.observeActive().first().first().essential)

        habits.softDelete(StarterHabits.ALL.first().id)
        habits.insertStarters(StarterHabits.ALL, names, nov(7))

        assertEquals(3, habits.observeActive().first().size)
        assertEquals(nov(6), habits.getAnyById(StarterHabits.ALL[1].id)!!.startDate)
    }

    @Test
    public fun reorderAndArchive() = runTest {
        val a = habits.create(HabitDraft("A", "#000000"), nov(1))
        val b = habits.create(HabitDraft("B", "#000000"), nov(1))
        habits.reorder(listOf(b, a))
        assertEquals(listOf("B", "A"), habits.observeActive().first().map { it.name })
        habits.setArchived(b, true)
        assertEquals(listOf("A"), habits.observeActive().first().map { it.name })
        assertEquals(listOf("B"), habits.observeArchived().first().map { it.name })
    }

    private fun nov(day: Int): LocalDate = LocalDate.of(2026, 11, day)
}
