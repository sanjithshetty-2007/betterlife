package com.sanjith.betterlife.data.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.repository.ReminderDraft
import com.sanjith.betterlife.data.repository.ReminderLogRepository
import com.sanjith.betterlife.data.repository.TaskDraft
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.domain.delivery.Pause
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReasonCode
import com.sanjith.betterlife.domain.model.ReminderLogStatus
import com.sanjith.betterlife.domain.model.ReminderState
import com.sanjith.betterlife.domain.settings.AppSettings
import com.sanjith.betterlife.domain.time.FixedClock
import com.sanjith.betterlife.reminders.engine.CalmState
import com.sanjith.betterlife.reminders.engine.RoomReminderStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** Prompt 12 state on the real phone storage: the calm store, Quiet Hours days, and the log queries. */
@RunWith(AndroidJUnit4::class)
public class CalmStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var file: File
    private lateinit var scope: CoroutineScope
    private val now = Instant.parse("2026-11-06T10:00:00Z")

    @Before
    public fun setUp() {
        file = File(context.cacheDir, "calm-test-${System.nanoTime()}.preferences_pb")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    public fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    public fun pauseDigestAndFatigueCountersSurviveAndClear() = runTest {
        val store = CalmStore(PreferenceDataStoreFactory.create(scope = scope) { file })
        assertEquals(CalmState(), store.read())

        val pause = Pause(now.plusSeconds(3_600), holdsCalendar = true, holdsAlarm = false)
        store.update { it.copy(pause = pause, digestAt = pause.until, ignoreStreak = 2, calmUntil = now.plusSeconds(86_400)) }
        assertEquals(CalmState(pause, pause.until, 2, now.plusSeconds(86_400)), store.read())

        store.update { it.copy(pause = null, digestAt = null, calmUntil = null) }
        assertEquals(CalmState(ignoreStreak = 2), store.read())
    }

    @Test
    public fun quietHoursDaysRoundTrip() = runTest {
        val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        repo.update { it.copy(quietHoursDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY)) }
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY), repo.current().quietHoursDays)
        assertEquals(DayOfWeek.entries.toSet(), AppSettings().quietHoursDays)
    }

    @Test
    public fun storeFindsHeldRemindersAndPopUpsThatStartedAGroup() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, BetterLifeDatabase::class.java).build()
        try {
            var id = 0
            val tasks = TaskRepository(db, { 1L }, { "id-${++id}" })
            val logs = ReminderLogRepository(db, { now.toEpochMilli() }, { "log-${++id}" })
            val reminderStore = RoomReminderStore(db, FixedClock(now, ZoneId.of("Asia/Kolkata")))
            tasks.create(TaskDraft("A", dueAtMillis = now.toEpochMilli(), dueZoneId = ZoneId.of("Asia/Kolkata")), listOf(ReminderDraft(0), ReminderDraft(10)))
            val (first, second) = reminderStore.loadTargets().map { it.reminderId }
            reminderStore.finish(first, ReminderState.HELD)
            assertEquals(listOf(first), reminderStore.heldReminderIds())

            fun ms(secondsAgo: Long) = now.minusSeconds(secondsAgo).toEpochMilli()
            logs.append(second, ReminderLogStatus.FIRED, Importance.FULL_SCREEN, null, "OVERLAY", ms(600))
            logs.append(second, ReminderLogStatus.FIRED, Importance.FULL_SCREEN, ReasonCode.FATIGUE_GROUPED, "OVERLAY", ms(300))
            logs.append(second, ReminderLogStatus.FIRED, Importance.NORMAL, null, "NOTIFICATION_ONLY", ms(200))
            logs.append(second, ReminderLogStatus.FIRED, Importance.FULL_SCREEN, null, "FULL_SCREEN_INTENT", ms(100))
            logs.append(second, ReminderLogStatus.FIRED, Importance.FULL_SCREEN, null, "OVERLAY", ms(4_000))

            assertEquals(
                listOf(now.minusSeconds(100), now.minusSeconds(600)),
                reminderStore.recentPopupStarts(now.minusSeconds(3_600)),
            )
        } finally {
            db.close()
        }
    }
}
