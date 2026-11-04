package com.sanjith.betterlife.reminders.popup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.popup.PopupCard
import com.sanjith.betterlife.domain.popup.SnoozeChoice
import com.sanjith.betterlife.domain.popup.SnoozeOption
import com.sanjith.betterlife.ui.theme.BetterLifeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
public class ReminderPopupUiTest {

    @get:Rule
    public val compose = createComposeRule()

    private val now = Instant.parse("2026-10-08T12:30:00Z")
    private val answers = mutableListOf<Pair<String, CardAction>>()
    private var ignoredTaps = 0

    private fun card(key: String, title: String, snoozes: Int = 0, importance: Importance = Importance.FULL_SCREEN) = PopupCard(
        key, key, title, "Bring the blue folder", importance, null, null, now, false, 60, null, snoozes, now,
    )

    private fun setContent(vararg cards: PopupCard, hideTitles: Boolean = false, tonightAllowed: Boolean = true) {
        val ui = PopupUi(
            hideTitles = hideTitles,
            reduceMotion = true,
            snoozeOptions = {
                SnoozeChoice.entries.map { choice ->
                    val allowed = choice != SnoozeChoice.TONIGHT || tonightAllowed
                    SnoozeOption(choice, if (choice == SnoozeChoice.PICK_TIME) null else now.plusSeconds(600), allowed)
                }
            },
            pickTime = { _, _ -> now.plusSeconds(3_600) },
            formatTime = { "6:00 PM" },
            formatDate = { "8 Oct" },
        )
        compose.setContent {
            BetterLifeTheme {
                ReminderPopupStack(cards.toList(), ui, onAction = { key, action -> answers += key to action }, onTapIgnored = { ignoredTaps++ })
            }
        }
    }

    private fun waitPastTapGuard() {
        Thread.sleep(TAP_GUARD_WAIT_MS)
        compose.waitForIdle()
    }

    @Test
    public fun cardShowsTitleNotesBadgeAndAllThreeButtons() {
        setContent(card("a", "Call mom"))
        compose.onNodeWithText("Call mom").assertIsDisplayed()
        compose.onNodeWithText("Bring the blue folder").assertIsDisplayed()
        compose.onNodeWithText("Pop-up").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("Snooze").assertIsDisplayed()
        compose.onNodeWithText("Reschedule").assertIsDisplayed()
        compose.onNodeWithContentDescription("Blip, curious").assertIsDisplayed()
    }

    @Test
    public fun tapsInTheFirst700MsAreIgnored() {
        setContent(card("a", "Call mom"))
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        assertTrue(answers.isEmpty())
        assertEquals(1, ignoredTaps)

        waitPastTapGuard()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        assertEquals(listOf("a" to CardAction.Done), answers)
    }

    @Test
    public fun snoozeOpensTheOptionsAndPickingOneAnswers() {
        setContent(card("a", "Call mom"), tonightAllowed = false)
        waitPastTapGuard()
        compose.onNodeWithText("Snooze").performClick()
        compose.onNodeWithText("10 min").assertIsDisplayed()
        compose.onNodeWithText("Pick time").assertIsDisplayed()
        compose.onNodeWithText("Tonight 6:00 PM").assertIsNotEnabled()

        compose.onNodeWithText("10 min").performClick()
        compose.waitForIdle()
        assertEquals(listOf("a" to CardAction.Snooze(now.plusSeconds(600))), answers)
    }

    @Test
    public fun thirdSnoozeSuggestsMovingToTomorrow() {
        setContent(card("a", "Call mom", snoozes = 3))
        compose.onNodeWithText("You have snoozed this 3 times. Move it to tomorrow instead?").assertIsDisplayed()
        compose.onNodeWithText("Move to tomorrow").assertIsDisplayed()
    }

    @Test
    public fun whyPanelExplainsTheRuleAndLevel() {
        setContent(card("a", "Call mom"))
        compose.onNodeWithContentDescription("Why am I seeing this?").performClick()
        compose.onNodeWithText("1 hour before").assertIsDisplayed()
        compose.onNodeWithText("Edit").assertIsDisplayed()
    }

    @Test
    public fun lockScreenPrivacyHidesTitleAndNotes() {
        setContent(card("a", "Secret plan"), hideTitles = true)
        compose.onNodeWithText("Reminder").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Secret plan").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Bring the blue folder").fetchSemanticsNodes().isEmpty())
    }

    @Test
    public fun severalRemindersAreStackedInOneList() {
        setContent(card("a", "Call mom"), card("b", "Pills", importance = Importance.ALARM))
        compose.onNodeWithText("2 reminders").assertIsDisplayed()
        compose.onNodeWithText("Call mom").assertIsDisplayed()
        compose.onNodeWithText("Pills").assertIsDisplayed()
    }

    private companion object {
        const val TAP_GUARD_WAIT_MS = 800L
    }
}
