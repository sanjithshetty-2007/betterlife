package com.sanjith.betterlife.feature.celebration

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.habit.CelebrationCenter
import com.sanjith.betterlife.data.habit.CelebrationEvent
import com.sanjith.betterlife.data.repository.HabitDraft
import com.sanjith.betterlife.data.repository.HabitRepository
import com.sanjith.betterlife.domain.mascot.MascotMood
import com.sanjith.betterlife.domain.mascot.MascotStage
import com.sanjith.betterlife.feature.habits.HabitService
import com.sanjith.betterlife.ui.mascot.MascotStatic
import com.sanjith.betterlife.ui.theme.BetterLifeTheme
import com.sanjith.betterlife.ui.theme.Dimens
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Celebrations fire once per milestone per streak, quietly after a backfill (Prompt 16); the screen shows its parts. */
@RunWith(AndroidJUnit4::class)
public class CelebrationTest {

    @get:Rule
    public val compose = createComposeRule()

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BetterLifeDatabase::class.java).build()
    private var idCounter = 0
    private val habits = HabitRepository(db, { 1_000L }, { "00000000-0000-4000-8000-%012d".format(++idCounter) })
    private val center = CelebrationCenter()
    private var today = LocalDate.of(2026, 11, 14)
    private val service = HabitService(habits, { today }, {}, center)

    @After
    public fun tearDown() {
        db.close()
    }

    private fun nov(day: Int) = LocalDate.of(2026, 11, day)

    @Test
    public fun sevenDaysTickedOnTheirOwnDayCelebrateOnceAndUntickRetickDoesNotRepeat() = runBlocking {
        val id = service.create(HabitDraft("Creatine", "#FF6B5E"))
        (14..20).forEach { d ->
            today = nov(d)
            service.tick(id, nov(d))
        }
        assertEquals(listOf(CelebrationEvent(id, "Creatine", 7, quiet = false)), center.full.value)

        center.shown(center.full.value.single())
        service.untick(id, nov(20))
        service.tick(id, nov(20))
        assertEquals(emptyList<CelebrationEvent>(), center.full.value)
        assertNull(center.quiet.value)
    }

    @Test
    public fun aBackfilledSeventhDayOnlyGetsTheQuietBanner() = runBlocking {
        val id = service.create(HabitDraft("Meditation", "#7E8CFF"))
        listOf(14, 15, 16, 18, 19, 20).forEach { d ->
            today = nov(d)
            service.tick(id, nov(d))
        }
        service.tick(id, nov(17))

        assertEquals(emptyList<CelebrationEvent>(), center.full.value)
        assertEquals(CelebrationEvent(id, "Meditation", 7, quiet = true), center.quiet.value)
        center.quietSeen(center.quiet.value!!)
        assertNull(center.quiet.value)
    }

    @Test
    public fun theCelebrationScreenShowsTheMilestoneHabitMascotAndButton() {
        var closed = false
        compose.setContent { BetterLifeTheme { CelebrationScreen(CelebrationEvent("h", "Creatine", 30, quiet = false), onClose = { closed = true }) } }
        compose.onNodeWithText("30").assertIsDisplayed()
        compose.onNodeWithText("days of Creatine!").assertIsDisplayed()
        compose.onNodeWithContentDescription("cheering", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Let's keep going").performClick()
        assertEquals(true, closed)
    }

    @Test
    public fun theStaticMascotDrawsInEveryMood() {
        compose.setContent {
            BetterLifeTheme {
                androidx.compose.foundation.layout.Column {
                    MascotMood.entries.forEach { MascotStatic(it, MascotStage.BABY, it.name, Modifier.size(Dimens.mascotMedium)) }
                    MascotStatic(MascotMood.CURIOUS, MascotStage.POD, "pod", Modifier.size(Dimens.mascotMedium))
                }
            }
        }
        MascotMood.entries.forEach { compose.onNodeWithContentDescription(it.name).assertIsDisplayed() }
        compose.onNodeWithContentDescription("pod").assertIsDisplayed()
    }
}
