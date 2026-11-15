package com.sanjith.betterlife.sync

import com.sanjith.betterlife.data.db.entity.HabitEntity
import com.sanjith.betterlife.data.db.entity.HabitLogEntity
import com.sanjith.betterlife.data.db.entity.PauseRangeEntity
import com.sanjith.betterlife.data.db.entity.SyncState
import com.sanjith.betterlife.domain.habit.HabitCategory
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import com.sanjith.betterlife.domain.habit.MilestoneDetector
import com.sanjith.betterlife.domain.habit.PauseReason
import com.sanjith.betterlife.domain.habit.StreakRun
import com.sanjith.betterlife.domain.sync.HabitDayKey
import com.sanjith.betterlife.domain.sync.HabitLogMerge
import com.sanjith.betterlife.domain.sync.RowMeta
import com.sanjith.betterlife.domain.sync.SyncPlanner
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Habit sync rules (Prompt S3): one row per habit day, quiet pulled milestones, and the server shape. */
public class HabitSyncRulesTest {

    private val day = HabitDayKey("creatine", "2026-11-02")
    private val stamp = "server_updated_at" to JsonPrimitive("2026-11-15T10:00:00.000001+00:00")

    // Pulls one server version of [day] against the phone's version and says whether the server wins.
    private fun serverWins(phone: RowMeta, server: RowMeta): Boolean {
        val local = HabitLogMerge.localVersions(listOf(day to server.id), mapOf(day to phone))
        return SyncPlanner.planPull(listOf(server to 1L), local).apply == listOf(server.id)
    }

    @Test
    public fun theSameHabitDayIsMatchedEvenUnderAnotherId() {
        val phone = RowMeta("phone-id", 100L, null)
        val versions = HabitLogMerge.localVersions(
            listOf(day to "server-id", HabitDayKey("creatine", "2026-11-03") to "other-day"),
            mapOf(day to phone),
        )
        assertEquals(mapOf("server-id" to phone), versions)
    }

    @Test
    public fun aLaterUntickOnTheServerBeatsAnEarlierTickOnThePhone() {
        assertEquals(true, serverWins(phone = RowMeta("a", 100L, null), server = RowMeta("b", 200L, 200L)))
    }

    @Test
    public fun aLaterTickOnThePhoneBeatsAnEarlierUntickOnTheServer() {
        assertEquals(false, serverWins(phone = RowMeta("a", 300L, null), server = RowMeta("b", 200L, 200L)))
    }

    @Test
    public fun aLaterTickOnTheServerBeatsAnEarlierUntickOnThePhone() {
        assertEquals(true, serverWins(phone = RowMeta("a", 300L, 300L), server = RowMeta("b", 400L, null)))
    }

    @Test
    public fun aLaterUntickOnThePhoneBeatsAnEarlierTickOnTheServer() {
        assertEquals(false, serverWins(phone = RowMeta("a", 500L, 500L), server = RowMeta("b", 400L, null)))
    }

    @Test
    public fun onATieAnUntickWins() {
        assertEquals(true, serverWins(phone = RowMeta("a", 100L, null), server = RowMeta("b", 100L, 100L)))
        assertEquals(false, serverWins(phone = RowMeta("a", 100L, 100L), server = RowMeta("b", 100L, null)))
    }

    @Test
    public fun pulledMilestonesAreRecordedQuietlyAndOnlyOnce() {
        val run = StreakRun(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 12), 12)
        assertEquals(listOf(7, 10), MilestoneDetector.reachedByPull(run, emptySet()))
        assertEquals(listOf(10), MilestoneDetector.reachedByPull(run, setOf(7)))
        assertEquals(emptyList<Int>(), MilestoneDetector.reachedByPull(run.copy(length = 6), emptySet()))
        assertEquals(listOf(7, 10, 15, 30), MilestoneDetector.reachedByPull(run.copy(length = 37), emptySet()))
    }

    @Test
    public fun habitLogsUpsertOnHabitAndDayEverythingElseOnId() {
        assertEquals("user_id,habit_id,local_date", SyncTables.upsertKey(SyncTables.HABIT_LOGS))
        assertEquals("id", SyncTables.upsertKey(SyncTables.HABITS))
        assertEquals("id", SyncTables.upsertKey(SyncTables.TASKS))
        // Reminders point at items, so they are pushed and pulled after every item table.
        assertEquals(SyncTables.REMINDERS, SyncTables.ALL.last())
        assertEquals(true, SyncTables.ALL.indexOf(SyncTables.HABITS) < SyncTables.ALL.indexOf(SyncTables.HABIT_LOGS))
    }

    @Test
    public fun habitRoundTripsAndReadsTheServersTimeFormat() {
        val habit = HabitEntity(
            id = "5a1f0c3e-0b1a-4c11-8000-000000000001", name = "Créatine 💊", colorHex = "#FF6B5E", avatar = "💊",
            category = HabitCategory.HEALTH, essential = true, archived = false, reminderTime = LocalTime.of(7, 30),
            sortOrder = 2, startDate = LocalDate.of(2026, 11, 3), createdAt = 1L, updatedAt = 2L, deletedAt = null,
            syncState = SyncState.DIRTY, serverUpdatedAt = null,
        )
        val json = EntityJson.habit(habit)
        assertEquals("07:30", (json["reminder_time"] as JsonPrimitive).content)
        val fromServer = JsonObject(json + ("reminder_time" to JsonPrimitive("07:30:00")) + stamp)
        val back = EntityJson.toHabit(fromServer)
        assertEquals(habit.copy(syncState = SyncState.CLEAN, serverUpdatedAt = back.serverUpdatedAt), back)
        assertEquals(null, EntityJson.toHabit(JsonObject(json + ("reminder_time" to kotlinx.serialization.json.JsonNull))).reminderTime)
    }

    @Test
    public fun habitLogAndPauseRangeRoundTrip() {
        val log = HabitLogEntity("log-1", "creatine", LocalDate.of(2026, 11, 2), HabitLogStatus.KEPT, 1L, 5L, 5L, SyncState.DIRTY, null)
        assertEquals(log.copy(syncState = SyncState.CLEAN), EntityJson.toHabitLog(EntityJson.habitLog(log)))
        assertEquals(day, EntityJson.habitDayKey(EntityJson.habitLog(log)))

        val open = PauseRangeEntity("p-1", LocalDate.of(2026, 11, 3), null, PauseReason.SICK, 1L, 2L, null, SyncState.DIRTY, null)
        assertEquals(open.copy(syncState = SyncState.CLEAN), EntityJson.toPauseRange(EntityJson.pauseRange(open)))
        val closed = open.copy(endDate = LocalDate.of(2026, 11, 5), reason = PauseReason.VACATION)
        assertEquals(closed.copy(syncState = SyncState.CLEAN), EntityJson.toPauseRange(EntityJson.pauseRange(closed)))
    }
}
