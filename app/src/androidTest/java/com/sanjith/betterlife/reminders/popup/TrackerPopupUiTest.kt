package com.sanjith.betterlife.reminders.popup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.popup.PopupCard
import com.sanjith.betterlife.domain.popup.DailyAsk
import com.sanjith.betterlife.domain.tracker.TrackerKind
import com.sanjith.betterlife.ui.theme.BetterLifeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/** A tracker's question in the pop-up (owner's features, 2026-11-21): target, amount boxes, Save, nothing else. */
@RunWith(AndroidJUnit4::class)
public class TrackerPopupUiTest {

    @get:Rule
    public val compose = createComposeRule()

    private val now = Instant.parse("2026-11-21T14:30:00Z")
    private val answers = mutableListOf<Pair<String, CardAction>>()

    private val card = PopupCard(
        "protein-2026-11-21", null, "How much protein have you consumed today?", null, Importance.ALARM,
        null, null, null, false, 0, null, 0, now, ask = DailyAsk(TrackerKind.PROTEIN, LocalDate.parse("2026-11-21"), 120),
    )

    private val sleepCard = PopupCard(
        "sleep-2026-11-20", null, "How much did you sleep last night?", null, Importance.ALARM,
        null, null, null, false, 0, null, 0, now, ask = DailyAsk(TrackerKind.SLEEP, LocalDate.parse("2026-11-20"), 480),
    )

    private fun setContent(shown: PopupCard = card) {
        val ui = PopupUi(
            hideTitles = false,
            reduceMotion = true,
            snoozeOptions = { emptyList() },
            pickTime = { _, _ -> null },
            formatTime = { "8:00 PM" },
            formatDate = { "21 Nov" },
        )
        compose.setContent {
            BetterLifeTheme { ReminderPopupStack(listOf(shown), ui, onAction = { key, action -> answers += key to action }, onTapIgnored = {}) }
        }
    }

    @Test
    public fun showsTargetQuestionAndSaveButNoDismissButtons() {
        setContent()
        compose.onNodeWithText("Daily target: 120g").assertIsDisplayed()
        compose.onNodeWithText("How much protein have you consumed today?").assertIsDisplayed()
        compose.onNodeWithText("Save Protein Intake").assertIsNotEnabled()
        listOf("Done", "Snooze", "Reschedule").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test
    public fun savingSendsTheGramsForTheCard() {
        setContent()
        compose.onNodeWithText("Enter amount in grams").performTextInput("95")
        Thread.sleep(TAP_GUARD_WAIT_MS)
        compose.waitForIdle()
        compose.onNodeWithText("Save Protein Intake").assertIsEnabled().performClick()
        compose.waitForIdle()

        assertEquals(listOf(card.key to CardAction.SaveAmount(95)), answers)
    }

    @Test
    public fun sleepCardTakesHoursAndMinutes() {
        setContent(sleepCard)
        compose.onNodeWithText("Sleep target: 8 h").assertIsDisplayed()
        compose.onNodeWithText("How much did you sleep last night?").assertIsDisplayed()
        compose.onNodeWithText("Hours").performTextInput("7")
        compose.onNodeWithText("Minutes").performTextInput("30")
        Thread.sleep(TAP_GUARD_WAIT_MS)
        compose.waitForIdle()
        compose.onNodeWithText("Save Sleep").assertIsEnabled().performClick()
        compose.waitForIdle()

        assertEquals(listOf(sleepCard.key to CardAction.SaveAmount(450)), answers)
    }

    private companion object {
        const val TAP_GUARD_WAIT_MS = 800L
    }
}
