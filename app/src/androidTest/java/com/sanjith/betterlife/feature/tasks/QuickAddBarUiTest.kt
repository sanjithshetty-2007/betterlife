package com.sanjith.betterlife.feature.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.domain.parser.AmPm
import com.sanjith.betterlife.domain.parser.QuickAddParser
import com.sanjith.betterlife.domain.time.FixedClock
import com.sanjith.betterlife.domain.time.zonedNow
import com.sanjith.betterlife.ui.theme.BetterLifeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class QuickAddBarUiTest {

    @get:Rule
    public val compose = createAndroidComposeRule<ComponentActivity>()

    private val now = FixedClock.of("2026-10-08T18:00", "Asia/Kolkata").zonedNow()
    private var submitted = 0

    /** Types like the owner would, then hides the keyboard so it cannot cover the preview. */
    private fun type(text: String) {
        compose.onNodeWithTag(QUICK_ADD_FIELD_TAG).performTextInput(text)
        compose.runOnUiThread {
            val window = compose.activity.window
            WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        }
        compose.waitForIdle()
    }

    private fun setContent() {
        compose.setContent {
            var text by remember { mutableStateOf("") }
            var answer by remember { mutableStateOf<AmPm?>(null) }
            val preview = text.takeIf { it.isNotBlank() }?.let { QuickAddParser.parse(it, now, answer) }
            // Scrollable, because typing opens the keyboard and shrinks the test window.
            BetterLifeTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                QuickAddBar(
                    text = text,
                    preview = preview,
                    formatDate = { it.toString() },
                    formatTime = { it.toString() },
                    onText = { text = it; answer = null },
                    onAmPm = { answer = it },
                    onSubmit = { submitted++ },
                )
                }
            }
        }
    }

    @Test
    public fun previewShowsWhatWasUnderstoodBeforeSaving() {
        setContent()
        type("submit report tomorrow 5pm !high #work @projectx")
        compose.onNodeWithText("📅 2026-10-09").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("⏰ 17:00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("High").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("#work").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("@projectx").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add").assertIsEnabled().performClick()
        assertEquals(1, submitted)
    }

    @Test
    public fun passedTimeShowsTheMovedToTomorrowWarning() {
        setContent()
        type("buy milk 5pm")
        compose.onNodeWithText("That time has passed today, so it is set for tomorrow.").performScrollTo().assertIsDisplayed()
    }

    @Test
    public fun unclearTimeAsksAmOrPmBeforeAddIsAllowed() {
        setContent()
        type("meeting at 5")
        compose.onNodeWithText("Add").assertIsNotEnabled()
        compose.onNodeWithText("PM").performClick()
        compose.onNodeWithText("⏰ 17:00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add").assertIsEnabled()
    }

    @Test
    public fun undatedTaskShowsTheInboxChip() {
        setContent()
        type("anniversary gift")
        compose.onNodeWithText("Inbox (no date)").performScrollTo().assertIsDisplayed()
    }
}
