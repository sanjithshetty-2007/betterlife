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
import com.sanjith.betterlife.domain.popup.ProteinPrompt
import com.sanjith.betterlife.ui.theme.BetterLifeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/** The protein question in the pop-up (owner's feature, 2026-11-21): target, grams box, Save, nothing else. */
@RunWith(AndroidJUnit4::class)
public class ProteinPopupUiTest {

    @get:Rule
    public val compose = createComposeRule()

    private val now = Instant.parse("2026-11-21T14:30:00Z")
    private val answers = mutableListOf<Pair<String, CardAction>>()

    private val card = PopupCard(
        "protein-2026-11-21", null, "How much protein have you consumed today?", null, Importance.ALARM,
        null, null, null, false, 0, null, 0, now, protein = ProteinPrompt(LocalDate.parse("2026-11-21"), 120),
    )

    private fun setContent() {
        val ui = PopupUi(
            hideTitles = false,
            reduceMotion = true,
            snoozeOptions = { emptyList() },
            pickTime = { _, _ -> null },
            formatTime = { "8:00 PM" },
            formatDate = { "21 Nov" },
        )
        compose.setContent {
            BetterLifeTheme { ReminderPopupStack(listOf(card), ui, onAction = { key, action -> answers += key to action }, onTapIgnored = {}) }
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

        assertEquals(listOf(card.key to CardAction.SaveProtein(95)), answers)
    }

    private companion object {
        const val TAP_GUARD_WAIT_MS = 800L
    }
}
