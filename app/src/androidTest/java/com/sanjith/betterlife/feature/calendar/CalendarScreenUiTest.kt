package com.sanjith.betterlife.feature.calendar

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.ui.theme.BetterLifeTheme
import com.sanjith.betterlife.ui.theme.Dimens
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The Calendar tab on screen (Prompt 18): month cells, the day sheet with Add event / Add task, week overlaps. */
@RunWith(AndroidJUnit4::class)
public class CalendarScreenUiTest {

    @get:Rule
    public val compose = createComposeRule()

    private val data = CalendarTestData(ApplicationProvider.getApplicationContext())
    private val newEvents = mutableListOf<Pair<EventType, LocalDate?>>()
    private val newTasks = mutableListOf<LocalDate>()

    @After
    public fun tearDown() {
        data.close()
    }

    private fun show(): CalendarViewModel {
        val vm = data.viewModel()
        val actions = CalendarActions(
            onOpen = {},
            onNewEvent = { type, date -> newEvents += type to date },
            onNewTask = { newTasks += it },
            onNewReminder = {},
            onOpenDefaults = {},
        )
        compose.setContent { BetterLifeTheme { CalendarScreen(actions, vm) } }
        return vm
    }

    @Test
    public fun todaysCellSaysWhatIsOnItAndOpensTheSheetWithBothAddButtons() {
        data.event("Physio session", data.today, "11:00", "12:00")
        data.task("Club poster due", data.today, "16:00")
        show()

        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithContentDescription("Friday 20 November, 2 items, today").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Friday 20 November, 2 items, today")
            .assertHeightIsAtLeast(Dimens.minTouchTarget)
            .performClick()

        compose.onNodeWithText("1 event · 1 task").assertExists()
        compose.onNodeWithText("Add task").performClick()
        assertEquals(listOf(data.today), newTasks)
    }

    @Test
    public fun addEventFromTheSheetStartsOnThatDay() {
        show()
        val day = "Wednesday 25 November, nothing"
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithContentDescription(day).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(day).performClick()
        // The sheet opens on the tapped day (Month's own today list may also say the day is empty).
        compose.onNodeWithText("Wednesday, 25 Nov").assertExists()
        compose.onNodeWithText("Add event").performClick()
        assertEquals(listOf(EventType.EVENT to LocalDate.of(2026, 11, 25)), newEvents)
    }

    @Test
    public fun theWeekShowsOverlappingEventsWithTheClashBadge() {
        data.event("Sync", data.today, "10:00", "11:00", "Work")
        data.event("Lab", data.today, "10:30", "11:30")
        show()
        compose.onNodeWithText("Week").performClick()

        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithContentDescription("Overlaps another event").fetchSemanticsNodes().size == 2 }
        compose.onNodeWithText("Sync").assertExists()
        compose.onNodeWithText("Lab").assertExists()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
