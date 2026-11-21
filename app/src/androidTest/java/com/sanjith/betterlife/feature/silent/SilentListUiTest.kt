package com.sanjith.betterlife.feature.silent

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.silent.SilentReminderRepository
import com.sanjith.betterlife.domain.silent.SilentReminder
import com.sanjith.betterlife.domain.silent.VibrationPattern
import com.sanjith.betterlife.ui.theme.BetterLifeTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import java.time.LocalTime

/** The Silent tab's list on real SQLite (owner's feature, 2026-11-21): empty text, a card, delete. */
@RunWith(AndroidJUnit4::class)
public class SilentListUiTest {

    // The database closes after the screen is gone (the outer rule ends last), so no query runs on a closed pool.
    @get:Rule(order = 0)
    public val database: ExternalResource = object : ExternalResource() {
        override fun after() {
            db.close()
        }
    }

    @get:Rule(order = 1)
    public val compose = createComposeRule()

    private lateinit var db: BetterLifeDatabase
    private lateinit var repository: SilentReminderRepository
    private val opened = mutableListOf<String>()

    @Before
    public fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BetterLifeDatabase::class.java).build()
        var id = 0
        repository = SilentReminderRepository(db, { 1_000L }, { "silent-${++id}" }) {}
    }

    private fun setContent() {
        val viewModel = SilentListViewModel(repository)
        compose.setContent { BetterLifeTheme { SilentList(viewModel, SnackbarHostState(), onOpen = { opened += it }) } }
    }

    @Test
    public fun emptyTabExplainsSilentReminders() {
        setContent()
        compose.onNodeWithText("No silent reminders yet", substring = true).assertIsDisplayed()
    }

    @Test
    public fun aReminderShowsItsVibrationAndLengthOpensAndDeletes() {
        runBlocking { repository.save(SilentReminder("", "Mewing", listOf(LocalTime.of(10, 0)), VibrationPattern.PULSE, 20, enabled = true)) }
        setContent()
        compose.waitUntil(TIMEOUT_MS) { runCatching { compose.onNodeWithText("Mewing").assertExists() }.isSuccess }

        compose.onNodeWithText("Pulse · 20 s").assertIsDisplayed()
        compose.onNodeWithText("Mewing").performClick()
        assertTrue(opened.single().isNotBlank())

        compose.onNodeWithContentDescription("Delete Mewing").performClick()
        compose.waitUntil(TIMEOUT_MS) { runBlocking { repository.active().isEmpty() } }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
