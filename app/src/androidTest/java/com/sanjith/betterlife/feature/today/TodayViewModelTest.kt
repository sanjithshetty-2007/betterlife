package com.sanjith.betterlife.feature.today

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.habit.MascotMoodSource
import com.sanjith.betterlife.data.repository.HabitDraft
import com.sanjith.betterlife.data.repository.HabitRepository
import com.sanjith.betterlife.data.repository.PauseRangeRepository
import com.sanjith.betterlife.data.repository.ProjectRepository
import com.sanjith.betterlife.data.repository.TaskDraft
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.data.settings.TodayStore
import com.sanjith.betterlife.domain.habit.HabitCategory
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import com.sanjith.betterlife.domain.habit.PauseReason
import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.domain.settings.AppSettings
import com.sanjith.betterlife.domain.time.FixedClock
import com.sanjith.betterlife.domain.today.ComebackTrigger
import com.sanjith.betterlife.domain.today.TodayKind
import com.sanjith.betterlife.domain.today.TodayRow
import com.sanjith.betterlife.feature.habits.HabitService
import com.sanjith.betterlife.feature.tasks.TaskService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Today on real SQLite (Prompt 17): ranking, ticks, Minimum Day's keep-the-rest, comeback and a pause range. */
@RunWith(AndroidJUnit4::class)
public class TodayViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FixedClock.of("2026-11-20T10:00", "Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 20)
    private lateinit var db: BetterLifeDatabase
    private lateinit var habits: HabitRepository
    private lateinit var tasks: TaskRepository
    private lateinit var store: TodayStore
    private lateinit var file: File
    private lateinit var scope: CoroutineScope
    private var idCounter = 0
    private var digests = 0

    @Before
    public fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, BetterLifeDatabase::class.java).build()
        val ids = { "00000000-0000-4000-8000-%012d".format(++idCounter) }
        habits = HabitRepository(db, { 1_000L }, ids)
        tasks = TaskRepository(db, { 1_000L }, ids)
        file = File(context.cacheDir, "today-test-${System.nanoTime()}.preferences_pb")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = TodayStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    @After
    public fun tearDown() {
        scope.cancel()
        file.delete()
        db.close()
    }

    private fun viewModel(checkComeback: Boolean = true): TodayViewModel {
        val habitService = HabitService(habits, { today }, {})
        return TodayViewModel(
            db = db,
            clock = clock,
            settings = MutableStateFlow(AppSettings(ownerName = "Sanjith")),
            source = TodaySource(db, clock),
            mood = MascotMoodSource(db, clock) { today },
            habitService = habitService,
            taskService = TaskService(tasks, ProjectRepository(db, { 1_000L }, { "p" }), clock) {},
            pauses = PauseRangeRepository(db, { 1_000L }, { "00000000-0000-4000-8000-9%011d".format(++idCounter) }),
            todayStore = store,
            endRangePause = { digests++ },
            checkComeback = checkComeback,
        )
    }

    private fun TodayViewModel.await(predicate: (TodaySnapshot) -> Boolean): TodaySnapshot = runBlocking {
        withTimeout(TIMEOUT_MS) { ui.first { it != null && predicate(it.snapshot) }!!.snapshot }
    }

    @Test
    public fun todayRanksATaskAndAHabitAndTickingAHabitSettlesIt() {
        runBlocking {
            tasks.create(TaskDraft("Pay bill", priority = Priority.HIGH, dueAtMillis = clock.now().plusSeconds(3_600).toEpochMilli(), dueZoneId = ZoneId.of("Asia/Kolkata")))
            habits.create(HabitDraft("Creatine", "#FF6B5E", essential = true), today.minusDays(1))
        }
        val vm = viewModel(checkComeback = false)
        val first = vm.await { it.rows.size == 2 }
        assertEquals(listOf(TodayKind.TASK, TodayKind.HABIT), first.rows.map { (it as TodayRow.Single).item.kind })
        assertEquals(1, first.tasksTotal)
        assertEquals("Sanjith", vm.ui.value!!.ownerName)

        vm.tick((first.rows[1] as TodayRow.Single).item)
        val after = vm.await { it.habitsDone == 1 }
        assertEquals(1, after.rows.size)
        assertEquals(1, after.bestStreak)
    }

    @Test
    public fun finishingMinimumDayKeepsEveryOtherHabit() {
        val (a, b) = runBlocking {
            habits.create(HabitDraft("Creatine", "#FF6B5E"), today) to habits.create(HabitDraft("Skincare", "#FF8FB8", category = HabitCategory.SELF_CARE), today)
        }
        val vm = viewModel(checkComeback = false)
        vm.await { it.habitsTotal == 2 }
        vm.tickHabit(a, HabitLogStatus.KEPT)
        vm.await { it.habitsDone == 1 }

        vm.keepTheRest()

        vm.await { it.habitsDone == 2 }
        assertEquals(HabitLogStatus.KEPT, runBlocking { db.habitLogDao().getForDay(b, today)!!.status })
    }

    @Test
    public fun comebackOpensAfterTwoDaysAwayOnceADay() {
        runBlocking { store.opened(today.minusDays(3)) }
        val vm = viewModel()
        val trigger = runBlocking { withTimeout(TIMEOUT_MS) { vm.comeback.first { it != null } } }
        assertEquals(ComebackTrigger.AWAY_FROM_APP, trigger)

        vm.comebackShown()
        runBlocking { withTimeout(TIMEOUT_MS) { while (store.read().comebackShownOn != today) kotlinx.coroutines.delay(POLL_MS) } }
        assertTrue(runBlocking { store.read() }.comebackReward)
        assertEquals(today, runBlocking { store.read() }.lastOpened)
    }

    @Test
    public fun aPauseRangeSettlesHabitsAndEndingItReleasesTheDigest() {
        runBlocking { habits.create(HabitDraft("Creatine", "#FF6B5E"), today.minusDays(5)) }
        val vm = viewModel(checkComeback = false)
        vm.await { it.habitsTotal == 1 }

        vm.startPause(today, today.plusDays(3), PauseReason.SICK)
        val paused = vm.await { it.activePause != null }
        assertEquals(1, paused.habitsDone)
        assertTrue(paused.rows.isEmpty())

        vm.endPause(paused.activePause!!.id)
        val after = vm.await { it.activePause == null }
        assertNull(after.activePause)
        assertEquals(1, digests)
        assertNotNull(runBlocking { db.pauseRangeDao().getAnyById(paused.activePause.id) }?.deletedAt)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 20L
    }
}
