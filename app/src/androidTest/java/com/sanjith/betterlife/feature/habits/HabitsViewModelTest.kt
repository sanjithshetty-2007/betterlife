package com.sanjith.betterlife.feature.habits

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.repository.HabitDraft
import com.sanjith.betterlife.data.repository.HabitRepository
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import com.sanjith.betterlife.domain.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/** The Habits screen's logic on real SQLite (Prompt 15): tick, untick, backfill, undo, months, archive. */
@RunWith(AndroidJUnit4::class)
public class HabitsViewModelTest {

    private lateinit var db: BetterLifeDatabase
    private lateinit var habits: HabitRepository
    private lateinit var service: HabitService
    private lateinit var viewModel: HabitsViewModel
    private val today = LocalDate.of(2026, 11, 20)
    private val settings = MutableStateFlow(AppSettings())
    private var reschedules = 0
    private var idCounter = 0

    @Before
    public fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BetterLifeDatabase::class.java).build()
        habits = HabitRepository(db, { 1_000L }, { "00000000-0000-4000-8000-%012d".format(++idCounter) })
        service = HabitService(habits, { today }) { reschedules++ }
        viewModel = HabitsViewModel(habits, service, settings) { change -> settings.value = change(settings.value) }
    }

    @After
    public fun tearDown() {
        db.close()
    }

    private fun ui(predicate: (HabitsUi) -> Boolean): HabitsUi = runBlocking {
        withTimeout(TIMEOUT_MS) { viewModel.ui.first { it != null && predicate(it) }!! }
    }

    private fun card(ui: HabitsUi): HabitCardUi = ui.cards!!.single()

    @Test
    public fun tickingTodayFillsTheBoxAndStartsTheStreak() {
        runBlocking { service.create(HabitDraft("Creatine", "#FF6B5E")) }
        val before = ui { it.cards?.size == 1 }
        assertEquals(31, card(before).boxes.size)

        viewModel.toggle(card(before), card(before).boxes[19])

        val after = ui { card(it).boxes[19].state == BoxState.DONE }
        assertEquals(1, card(after).streak.current)
        assertEquals(1, after.doneToday)
        assertEquals(HabitUndo(card(after).id, "Creatine", today, ticked = true, before = null), viewModel.undo.value)
    }

    @Test
    public fun untickingClearsTheDayAndUndoBringsItBack() {
        val id = runBlocking { habits.create(HabitDraft("Meditation", "#7E8CFF"), LocalDate.of(2026, 11, 1)) }
        runBlocking { habits.tick(id, today, today, HabitLogStatus.KEPT) }
        val ticked = ui { card(it).boxes[19].state == BoxState.KEPT }

        viewModel.toggle(card(ticked), card(ticked).boxes[19])
        ui { card(it).boxes[19].state == BoxState.OPEN }
        val offer = viewModel.undo.value!!
        assertFalse(offer.ticked)

        viewModel.undo(offer)
        ui { card(it).boxes[19].state == BoxState.KEPT }
        assertNull(viewModel.undo.value)
    }

    @Test
    public fun backfillingAPastDayJoinsTheStreak() {
        val id = runBlocking { habits.create(HabitDraft("Skincare", "#FF8FB8"), LocalDate.of(2026, 11, 1)) }
        runBlocking { listOf(17, 19, 20).forEach { habits.tick(id, LocalDate.of(2026, 11, it), today) } }
        val gap = ui { card(it).streak.current == 2 }

        viewModel.toggle(card(gap), card(gap).boxes[17])

        val joined = ui { card(it).streak.current == 4 }
        assertEquals(4, card(joined).streak.best)
        // Future and greyed boxes do nothing.
        viewModel.toggle(card(joined), card(joined).boxes[24])
        assertEquals(4, card(ui { true }).streak.current)
    }

    @Test
    public fun monthsGoBackButNeverPastThisMonth() {
        runBlocking { service.create(HabitDraft("Haircare", "#5EC4D6")) }
        ui { it.isCurrentMonth }

        viewModel.previousMonth()
        val october = ui { it.month == YearMonth.of(2026, 10) }
        assertFalse(october.isCurrentMonth)
        assertTrue(card(october).boxes.all { it.state == BoxState.BEFORE_START })

        viewModel.nextMonth()
        ui { it.isCurrentMonth }
        viewModel.nextMonth()
        assertEquals(YearMonth.of(2026, 11), ui { true }.month)

        viewModel.previousMonth()
        viewModel.previousMonth()
        ui { it.month == YearMonth.of(2026, 9) }
        viewModel.backToThisMonth()
        ui { it.isCurrentMonth }
    }

    @Test
    public fun anArchivedHabitLeavesTheListAndItsReminderIsRescheduled() {
        val id = runBlocking { service.create(HabitDraft("Read", "#7BC67E")) }
        ui { it.cards?.size == 1 }
        val before = reschedules

        runBlocking { service.setArchived(id, true) }

        ui { it.cards?.isEmpty() == true }
        assertEquals(before + 1, reschedules)
    }

    @Test
    public fun theVibrationSettingIsSaved() {
        assertTrue(ui { true }.haptics)
        viewModel.setHaptics(false)
        ui { !it.haptics }
        assertFalse(settings.value.habitTickHaptics)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
