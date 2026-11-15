package com.sanjith.betterlife.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.entity.HabitEntity
import com.sanjith.betterlife.data.db.entity.HabitLogEntity
import com.sanjith.betterlife.data.db.entity.PauseRangeEntity
import com.sanjith.betterlife.data.db.entity.SyncState
import com.sanjith.betterlife.data.repository.HabitDraft
import com.sanjith.betterlife.data.repository.HabitRepository
import com.sanjith.betterlife.data.repository.PauseRangeRepository
import com.sanjith.betterlife.data.repository.ReminderDraft
import com.sanjith.betterlife.data.repository.toEntity
import com.sanjith.betterlife.domain.habit.HabitCategory
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import com.sanjith.betterlife.domain.habit.PauseReason
import com.sanjith.betterlife.domain.habit.StarterHabit
import com.sanjith.betterlife.domain.habit.StarterHabits
import com.sanjith.betterlife.domain.habit.StreakCalculator
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.sync.RowMeta
import com.sanjith.betterlife.domain.sync.SyncPlanner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/** Habit sync on real SQLite (Prompt S3): what is pushed, the habit-day merge, quiet milestones, pauses, starters. */
@RunWith(AndroidJUnit4::class)
public class HabitSyncStoreTest {

    private lateinit var db: BetterLifeDatabase
    private lateinit var habits: HabitRepository
    private lateinit var pauses: PauseRangeRepository
    private lateinit var store: RoomSyncStore
    private var now = 1_000L
    private var idCounter = 0
    private var today = nov(8)
    private val newId = { "00000000-0000-4000-8000-%012d".format(++idCounter) }

    @Before
    public fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BetterLifeDatabase::class.java).build()
        habits = HabitRepository(db, { now }, newId)
        pauses = PauseRangeRepository(db, { now }, newId)
        store = RoomSyncStore(db) { logs -> habits.recordPulledMilestones(logs, today) }
    }

    @After
    public fun tearDown() {
        db.close()
    }

    @Test
    public fun realHabitDataIsPushedButDebugSeedHabitsNeverLeaveThePhone() = runTest {
        val real = habits.create(HabitDraft("Creatine", "#FF6B5E", reminderTime = LocalTime.of(21, 30)), nov(1))
        habits.tick(real, today, today)
        pauses.start(nov(10), nov(12), PauseReason.VACATION)
        val seed = "debug-seed-habit-0"
        db.habitDao().insertIfAbsent(listOf(habit(seed, "Seed habit", updatedAt = now)))
        db.habitLogDao().insertAll(listOf(HabitLogEntity(newId(), seed, today, HabitLogStatus.DONE, now, now, null, SyncState.DIRTY, null)))
        db.reminderDao().insertAll(listOf(ReminderDraft(0).toEntity(newId(), ReminderItemType.HABIT, seed, now)))

        assertEquals(listOf(real), store.dirty(SyncTables.HABITS, 100).map { text(it, "id") })
        assertEquals(listOf(real), store.dirty(SyncTables.HABIT_LOGS, 100).map { text(it, "habit_id") })
        assertEquals(1, store.dirty(SyncTables.PAUSE_RANGES, 100).size)
        val reminders = store.dirty(SyncTables.REMINDERS, 100)
        assertEquals(listOf("HABIT" to real), reminders.map { text(it, "item_type") to text(it, "item_id") })
        assertEquals(4, db.syncDao().observePendingCount().first())
    }

    @Test
    public fun aPulledVersionOfTheSameDayUnderAnotherIdReplacesThePhonesOlderCopy() = runTest {
        val id = habits.create(HabitDraft("Creatine", "#FF6B5E"), nov(1))
        habits.tick(id, today, today)
        val phoneCopy = db.habitLogDao().getForDay(id, today)!!
        val serverUntick = log("00000000-0000-4000-8000-000000000999", id, today, updatedAt = 2_000L, deletedAt = 2_000L)

        val versions = store.localVersions(SyncTables.HABIT_LOGS, listOf(serverUntick))
        assertEquals(mapOf(text(serverUntick, "id") to RowMeta(phoneCopy.id, 1_000L, null)), versions)
        val plan = SyncPlanner.planPull(listOf(EntityJson.meta(serverUntick) to 1L), versions)
        assertEquals(listOf(text(serverUntick, "id")), plan.apply)
        store.applyRemote(SyncTables.HABIT_LOGS, listOf(serverUntick))

        val merged = db.habitLogDao().getForDay(id, today)!!
        assertEquals("00000000-0000-4000-8000-000000000999", merged.id)
        assertEquals(2_000L, merged.deletedAt)
        assertEquals(SyncState.CLEAN, merged.syncState)
        // Only the habit itself still waits to be pushed; the merged day arrived from the server.
        assertEquals(1, db.syncDao().observePendingCount().first())
    }

    @Test
    public fun aNewerTickOnThePhoneIsNotOverwrittenByAnOlderServerUntick() = runTest {
        val id = habits.create(HabitDraft("Creatine", "#FF6B5E"), nov(1))
        now = 3_000L
        habits.tick(id, today, today)
        val serverUntick = log("00000000-0000-4000-8000-000000000998", id, today, updatedAt = 2_000L, deletedAt = 2_000L)

        val plan = SyncPlanner.planPull(listOf(EntityJson.meta(serverUntick) to 1L), store.localVersions(SyncTables.HABIT_LOGS, listOf(serverUntick)))

        assertEquals(emptyList<String>(), plan.apply)
        assertNull(db.habitLogDao().getForDay(id, today)!!.deletedAt)
    }

    @Test
    public fun pulledLogsRecomputeTheStreakAndRecordMilestonesQuietly() = runTest {
        val id = habits.create(HabitDraft("Creatine", "#FF6B5E"), nov(1))
        val pulled = (1..8).map { day -> log("00000000-0000-4000-8000-%012d".format(500 + day), id, nov(day), updatedAt = 4_000L) }

        store.applyRemote(SyncTables.HABIT_LOGS, pulled)
        store.applyRemote(SyncTables.HABIT_LOGS, pulled)

        assertEquals(8, StreakCalculator.current(habits.history(id)!!, today))
        val ledger = db.milestoneLedgerDao().getForHabit(id)
        assertEquals(listOf(7), ledger.map { it.milestone })
        assertEquals(true, ledger.single().quiet)
        // The next real tick does not announce the pulled 7 again.
        today = nov(9)
        val result = habits.tick(id, today, today) as com.sanjith.betterlife.data.repository.HabitEditResult.Saved
        assertNull(result.milestone)
    }

    @Test
    public fun aPulledPauseRangeBridgesTheStreakWithoutCounting() = runTest {
        val id = habits.create(HabitDraft("Creatine", "#FF6B5E"), nov(1))
        val range = PauseRangeEntity("00000000-0000-4000-8000-000000000700", nov(4), nov(4), PauseReason.SICK, 1L, 2L, null, SyncState.DIRTY, null)
        store.applyRemote(SyncTables.PAUSE_RANGES, listOf(server(EntityJson.pauseRange(range))))
        val days = listOf(1, 2, 3, 5, 6)
        store.applyRemote(SyncTables.HABIT_LOGS, days.map { log("00000000-0000-4000-8000-%012d".format(600 + it), id, nov(it), updatedAt = 4_000L) })

        assertEquals(SyncState.CLEAN, db.pauseRangeDao().getAnyById(range.id)!!.syncState)
        assertEquals(5, StreakCalculator.current(habits.history(id)!!, nov(6)))
    }

    @Test
    public fun aReseededStarterLosesToTheOwnersSyncedVersion() = runTest {
        now = 9_000L
        habits.insertStarters(StarterHabits.ALL, { key: StarterHabit.Key -> key.name }, nov(15))
        val creatine = StarterHabits.ALL.first().id
        assertEquals(HabitRepository.STARTER_UPDATED_AT, habits.getAnyById(creatine)!!.updatedAt)
        val owners = server(EntityJson.habit(habit(creatine, "Creatine 5g", updatedAt = 4_000L).copy(startDate = nov(3))))

        val plan = SyncPlanner.planPull(listOf(EntityJson.meta(owners) to 1L), store.localVersions(SyncTables.HABITS, listOf(owners)))
        assertEquals(listOf(creatine), plan.apply)
        store.applyRemote(SyncTables.HABITS, listOf(owners))

        val after = habits.getAnyById(creatine)!!
        assertEquals("Creatine 5g", after.name)
        assertEquals(nov(3), after.startDate)
        assertEquals(SyncState.CLEAN, after.syncState)
    }

    private fun habit(id: String, name: String, updatedAt: Long) = HabitEntity(
        id = id, name = name, colorHex = "#FF6B5E", avatar = null, category = HabitCategory.HEALTH, essential = false,
        archived = false, reminderTime = null, sortOrder = 0, startDate = nov(1), createdAt = 1L, updatedAt = updatedAt,
        deletedAt = null, syncState = SyncState.DIRTY, serverUpdatedAt = null,
    )

    private fun log(id: String, habitId: String, date: LocalDate, updatedAt: Long, deletedAt: Long? = null): JsonObject =
        server(EntityJson.habitLog(HabitLogEntity(id, habitId, date, HabitLogStatus.DONE, 1L, updatedAt, deletedAt, SyncState.CLEAN, null)))

    private fun server(row: JsonObject) = JsonObject(row + ("server_updated_at" to JsonPrimitive("2026-11-15T10:00:00.000001+00:00")))

    private fun text(row: JsonObject, key: String) = (row[key] as JsonPrimitive).content

    private fun nov(day: Int): LocalDate = LocalDate.of(2026, 11, day)
}
