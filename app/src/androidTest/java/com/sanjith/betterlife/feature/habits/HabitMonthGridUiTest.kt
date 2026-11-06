package com.sanjith.betterlife.feature.habits

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.domain.habit.HabitHistory
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.BetterLifeTheme
import com.sanjith.betterlife.ui.theme.Dimens
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/** The 31-box grid on screen (Prompt 15): box count, greyed boxes, touch size, labels. */
@RunWith(AndroidJUnit4::class)
public class HabitMonthGridUiTest {

    @get:Rule
    public val compose = createComposeRule()

    private val today = LocalDate.of(2026, 2, 20)
    private val tapped = mutableListOf<Int>()

    private fun show(start: LocalDate) {
        val history = HabitHistory(start, mapOf(LocalDate.of(2026, 2, 12) to HabitLogStatus.DONE))
        val boxes = HabitGridModel.boxes(history, YearMonth.of(2026, 2), today)
        compose.setContent {
            BetterLifeTheme {
                // About a phone's width inside a card: 7 cells of at least 48 dp.
                HabitMonthGrid(boxes, BetterLifeColors.CategoryPalette.first(), { tapped += it.day }, Modifier.width(Dimens.minTouchTarget * 7))
            }
        }
    }

    @Test
    public fun thirtyOneBoxesWithFebruarysExtraDaysAndPreStartDaysGreyed() {
        show(start = LocalDate.of(2026, 2, 10))
        val all = compose.onAllNodesWithTag(BOX_TAG, useUnmergedTree = true).fetchSemanticsNodes().size +
            compose.onAllNodesWithTag(GREYED_TAG, useUnmergedTree = true).fetchSemanticsNodes().size
        assertEquals(31, all)
        // 1..9 before the start + 29, 30, 31 that February 2026 does not have.
        assertEquals(12, compose.onAllNodesWithTag(GREYED_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("Day 30, not in this month").assertIsNotEnabled()
        // Greyed, but a tick still counts: it moves the start date back.
        compose.onNodeWithContentDescription("Day 5, before this habit started").assertIsEnabled()
    }

    @Test
    public fun everyBoxIsAFortyEightDpTargetAndOnlyRealPastDaysTap() {
        show(start = LocalDate.of(2026, 1, 1))
        compose.onNodeWithContentDescription("Day 12, done").assertIsEnabled().assertHeightIsAtLeast(Dimens.minTouchTarget).assertWidthIsAtLeast(Dimens.minTouchTarget)
        compose.onNodeWithContentDescription("Day 20, today, not done yet").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Day 21, still to come").assertIsNotEnabled().performClick()
        assertEquals(listOf(20), tapped)
    }
}
