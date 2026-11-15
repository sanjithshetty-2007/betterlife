package com.sanjith.betterlife.feature.calendar

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.domain.calendar.CalendarView
import com.sanjith.betterlife.domain.event.AgendaKind
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay

/** The calendar on real SQLite (Prompt 18): visible range only, repeats, filter, week start, grid and day sheet. */
@RunWith(AndroidJUnit4::class)
public class CalendarViewModelTest {

    private val data = CalendarTestData(ApplicationProvider.getApplicationContext())
    private val today = data.today

    @After
    public fun tearDown() {
        data.close()
    }

    @Test
    public fun aWeeklyMeetingAndABirthdayShowInAFutureMonthAndOnlyThatRangeIsLoaded() {
        data.event("Standup", LocalDate.of(2026, 11, 2), "09:00", "09:15", "Work", RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY)), com.sanjith.betterlife.domain.model.EventType.MEETING)
        data.birthday("Mom", MonthDay.of(1, 14))
        data.event("Far away", LocalDate.of(2027, 6, 1), "10:00", "11:00")
        val vm = data.viewModel()
        vm.await { it.view == CalendarView.MONTH }
        vm.next()
        vm.next() // January 2027: 28 Dec to 31 Jan with Monday first
        val january = vm.await { it.anchor.monthValue == 1 }

        val standups = january.days.filter { day -> day.entries.any { it.item.title == "Standup" } }.map { it.date }
        assertEquals(listOf(28, 4, 11, 18, 25), standups.map { it.dayOfMonth })
        assertTrue(january.marks.getValue(LocalDate.of(2027, 1, 14)).birthday)
        assertFalse(january.days.any { day -> day.entries.any { it.item.title == "Far away" } })
        assertEquals(LocalDate.of(2026, 12, 28), january.range.from)
    }

    @Test
    public fun theCategoryFilterHidesOtherCategoriesButKeepsTasks() {
        data.event("Review", today, "11:00", "12:00", "Work")
        data.event("Dinner", today, "19:00", "21:00", "Personal")
        data.task("Pay bill", today, "16:00")
        val vm = data.viewModel()
        vm.await { it.day(today)?.entries?.size == 3 }

        vm.toggleCategory("Work")
        val filtered = vm.await { it.filter.categories.isNotEmpty() }

        assertEquals(setOf("Review", "Pay bill"), filtered.day(today)!!.entries.map { it.item.title }.toSet())
    }

    @Test
    public fun switchingTheWeekStartRebuildsTheGrid() {
        val vm = data.viewModel()
        assertEquals(DayOfWeek.MONDAY, vm.await { it.monthGrid != null }.monthGrid!!.days.first().dayOfWeek)

        data.weekStart.value = DayOfWeek.SUNDAY

        val sunday = vm.await { it.weekStart == DayOfWeek.SUNDAY }
        assertEquals(LocalDate.of(2026, 11, 1), sunday.monthGrid!!.days.first())
        vm.select(CalendarView.WEEK)
        assertEquals(LocalDate.of(2026, 11, 15), vm.await { it.view == CalendarView.WEEK }.range.from)
    }

    @Test
    public fun theWeekGridPutsOverlapsSideBySideAndOnlyEventsClash() {
        data.event("Sync", today, "10:00", "11:00", "Work")
        data.event("Lab", today, "10:30", "11:30")
        data.task("Call", today, "10:15")
        val vm = data.viewModel()
        vm.select(CalendarView.WEEK)
        val week = vm.await { it.view == CalendarView.WEEK && it.grid.any { day -> day.blocks.size == 3 } }

        val blocks = week.grid.single { it.date == today }.blocks
        assertEquals(setOf(3), blocks.map { it.columns }.toSet())
        assertEquals(setOf(0, 1, 2), blocks.map { it.column }.toSet())
        assertEquals(setOf("Sync", "Lab"), blocks.filter { it.entry.conflict }.map { it.entry.item.title }.toSet())
        assertEquals(7, week.grid.size)
    }

    @Test
    public fun theDaySheetHasTheDaysEventsAndTasksTogether() {
        val tomorrow = today.plusDays(1)
        data.event("Physio", tomorrow, "11:00", "12:00")
        data.task("Poster due", tomorrow, "16:00")
        val vm = data.viewModel()
        vm.await { it.day(tomorrow) != null }

        vm.openDay(tomorrow)
        val open = vm.await { it.sheetDay == tomorrow }
        assertEquals(listOf(AgendaKind.EVENT, AgendaKind.TASK), open.day(tomorrow)!!.entries.map { it.item.kind })

        vm.closeSheet()
        assertNull(vm.await { it.sheetDay == null }.sheetDay)
    }
}
