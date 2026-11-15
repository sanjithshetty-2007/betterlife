package com.sanjith.betterlife.widgets

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.entity.HabitLogEntity
import com.sanjith.betterlife.data.db.entity.SyncState
import com.sanjith.betterlife.data.habit.CelebrationCenter
import com.sanjith.betterlife.data.repository.HabitDraft
import com.sanjith.betterlife.data.repository.HabitEditResult
import com.sanjith.betterlife.data.repository.HabitRepository
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import com.sanjith.betterlife.domain.habit.StreakCalculator
import com.sanjith.betterlife.domain.time.FixedClock
import com.sanjith.betterlife.domain.time.habitToday
import com.sanjith.betterlife.feature.habits.HabitService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/** The habit widget's tap on real SQLite (Prompt 19): the same as an in-app tick, the 01:00 day, no lost updates. */
@RunWith(AndroidJUnit4::class)
public class HabitWidgetToggleTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BetterLifeDatabase::class.java).build()
    private val ids = AtomicInteger()
    private val habits = HabitRepository(db, { 1_000L }) { "00000000-0000-4000-8000-%012d".format(ids.incrementAndGet()) }
    private val celebrations = CelebrationCenter()
    private var clock = FixedClock.of("2026-11-15T10:00", "Asia/Kolkata")
    private val service = HabitService(habits, { clock.habitToday(ROLLOVER) }, {}, celebrations)
    private val toggle = HabitWidgetToggle(service, habits)
    private val nov15 = LocalDate.of(2026, 11, 15)

    @After
    public fun tearDown() {
        db.close()
    }

    private fun create(name: String): String = runBlocking { habits.create(HabitDraft(name, "#FF6B5E"), nov15.minusDays(10)) }

    private fun log(habitId: String, date: LocalDate) =
        HabitLogEntity("log-$habitId-$date", habitId, date, HabitLogStatus.DONE, 1L, 1L, null, SyncState.DIRTY, null)

    @Test
    public fun aWidgetTapGivesTheSameResultAsTickingInTheApp() {
        val fromWidget = create("Creatine")
        val fromApp = create("Meditation")
        runBlocking {
            // Six days done already: today's tick makes 7, a full celebration either way.
            db.habitLogDao().insertAll((1..6).flatMap { listOf(log(fromWidget, nov15.minusDays(it.toLong())), log(fromApp, nov15.minusDays(it.toLong()))) })
            assertTrue(toggle.toggle(fromWidget) is HabitEditResult.Saved)
            service.tick(fromApp, nov15)
        }
        val widgetDay = runBlocking { db.habitLogDao().getForDay(fromWidget, nov15) }!!
        val appDay = runBlocking { db.habitLogDao().getForDay(fromApp, nov15) }!!
        assertEquals(appDay.status to appDay.deletedAt, widgetDay.status to widgetDay.deletedAt)
        assertEquals(7, StreakCalculator.current(runBlocking { habits.history(fromWidget) }!!, nov15))
        assertEquals(listOf(7, 7), celebrations.full.value.map { it.milestone })
        assertEquals(setOf(fromWidget, fromApp), celebrations.full.value.map { it.habitId }.toSet())
        assertEquals(listOf(7), runBlocking { db.milestoneLedgerDao().getForHabit(fromWidget) }.map { it.milestone })
    }

    @Test
    public fun aSecondTapClearsTodayAndATapAtOneInTheMorningCountsForYesterday() {
        val id = create("Creatine")
        runBlocking { toggle.toggle(id) }
        runBlocking { toggle.toggle(id) }
        assertNotNull(runBlocking { db.habitLogDao().getForDay(id, nov15) }!!.deletedAt)

        clock = FixedClock.of("2026-11-16T01:00", "Asia/Kolkata")
        runBlocking { toggle.toggle(id) }
        // Before the 03:00 rollover the habit day is still the 15th: the box ticked again, not the 16th.
        assertNull(runBlocking { db.habitLogDao().getForDay(id, nov15) }!!.deletedAt)
        assertNull(runBlocking { db.habitLogDao().getForDay(id, nov15.plusDays(1)) })
    }

    @Test
    public fun theAppAndTheWidgetWritingAtOnceLoseNothing() {
        val names = (1..8).map { create("Habit $it") }
        runBlocking {
            // Every habit ticked from the widget and from the app at the same moment, on many threads.
            names.flatMap { id -> listOf(async(Dispatchers.IO) { toggle.toggle(id) }, async(Dispatchers.IO) { service.tick(id, nov15) }) }.awaitAll()
        }
        names.forEach { id ->
            val day = runBlocking { db.habitLogDao().getForDay(id, nov15) }
            // One row per habit and day (the unique index), and it exists: no update was lost. Two ticks
            // can meet as tick-then-clear only if the widget saw the app's tick first, which leaves a
            // cleared row, never a missing or duplicated one.
            assertNotNull(day)
        }
        val rows = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM habit_logs WHERE localDate = '2026-11-15'").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
        assertEquals(names.size, rows)
    }

    private companion object {
        const val ROLLOVER = 3
    }
}
