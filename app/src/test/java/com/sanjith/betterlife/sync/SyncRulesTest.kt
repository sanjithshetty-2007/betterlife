package com.sanjith.betterlife.sync

import com.sanjith.betterlife.data.db.entity.EventEntity
import com.sanjith.betterlife.data.db.entity.ReminderEntity
import com.sanjith.betterlife.data.db.entity.SyncState
import com.sanjith.betterlife.data.db.entity.TaskEntity
import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.model.ReminderState
import com.sanjith.betterlife.domain.sync.ConflictResolver
import com.sanjith.betterlife.domain.sync.Resolution
import com.sanjith.betterlife.domain.sync.RowMeta
import com.sanjith.betterlife.domain.sync.SyncCursor
import com.sanjith.betterlife.domain.sync.SyncError
import com.sanjith.betterlife.domain.sync.SyncIndicator
import com.sanjith.betterlife.domain.sync.SyncPlanner
import com.sanjith.betterlife.domain.sync.SyncStatusRules
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Conflict rule, pull plan, cursor, indicator and the JSON shape of each table (Prompt S2). */
public class SyncRulesTest {

    @Test
    public fun lastWriteWinsAndTombstonesWinTies() {
        assertEquals(Resolution.TAKE_REMOTE, ConflictResolver.resolve(null, RowMeta("a", 1, null)))
        assertEquals(Resolution.TAKE_REMOTE, ConflictResolver.resolve(RowMeta("a", 1, null), RowMeta("a", 2, null)))
        assertEquals(Resolution.KEEP_LOCAL, ConflictResolver.resolve(RowMeta("a", 3, null), RowMeta("a", 2, null)))
        assertEquals(Resolution.TAKE_REMOTE, ConflictResolver.resolve(RowMeta("a", 2, null), RowMeta("a", 2, 2)))
        assertEquals(Resolution.KEEP_LOCAL, ConflictResolver.resolve(RowMeta("a", 2, 2), RowMeta("a", 2, null)))
        // The same version (this phone's own push coming back) is not applied again.
        assertEquals(Resolution.KEEP_LOCAL, ConflictResolver.resolve(RowMeta("a", 2, null), RowMeta("a", 2, null)))
        // A newer edit beats an older delete: an undo of a delete travels like any other edit.
        assertEquals(Resolution.TAKE_REMOTE, ConflictResolver.resolve(RowMeta("a", 2, 2), RowMeta("a", 3, null)))
    }

    @Test
    public fun pullPlanAppliesOnlyNewerRowsAndMovesTheCursorToTheLastRow() {
        val plan = SyncPlanner.planPull(
            listOf(RowMeta("a", 5, null) to 100L, RowMeta("b", 5, null) to 101L, RowMeta("c", 9, null) to 102L),
            mapOf("a" to RowMeta("a", 5, null), "b" to RowMeta("b", 7, null)),
        )
        assertEquals(listOf("c"), plan.apply)
        assertEquals(102L, plan.nextCursorMicros)
    }

    @Test
    public fun cursorKeepsMicroseconds() {
        val t = Instant.parse("2026-11-06T11:07:33.725123Z")
        assertEquals(t, SyncCursor.fromMicros(SyncCursor.toMicros(t)))
        assertEquals("2026-11-06T11:07:33.725123Z", EntityJson.cursorText(SyncCursor.toMicros(t)))
        assertEquals(SyncCursor.toMicros(t), EntityJson.serverMicros(JsonObject(mapOf("server_updated_at" to JsonPrimitive("2026-11-06T11:07:33.725123+00:00")))))
    }

    @Test
    public fun indicatorShowsTheMostUsefulState() {
        assertEquals(SyncIndicator.OFF, SyncStatusRules.indicator(false, false, 0, null))
        assertEquals(SyncIndicator.SIGNED_OUT, SyncStatusRules.indicator(true, false, 3, null))
        assertEquals(SyncIndicator.OFFLINE, SyncStatusRules.indicator(true, true, 3, SyncError.OFFLINE))
        assertEquals(SyncIndicator.SERVER_PAUSED, SyncStatusRules.indicator(true, true, 0, SyncError.SERVER_PAUSED))
        assertEquals(SyncIndicator.ERROR, SyncStatusRules.indicator(true, true, 0, SyncError.SESSION_EXPIRED))
        assertEquals(SyncIndicator.PENDING, SyncStatusRules.indicator(true, true, 2, null))
        assertEquals(SyncIndicator.SYNCED, SyncStatusRules.indicator(true, true, 0, null))
    }

    @Test
    public fun taskRoundTripsThroughTheServerShape() {
        val task = TaskEntity(
            "1e0d6a1c-0000-4000-8000-000000000001", "Pay bill", "notes", 1_800_000_000_000L, ZoneId.of("Asia/Kolkata"), null,
            Priority.HIGH, null, null, "home", "DAILY", false, Importance.FULL_SCREEN, null, 1L, 2L, null, SyncState.DIRTY, null,
        )
        val json = EntityJson.task(task)
        assertFalse(json.containsKey("user_id"))
        assertFalse(json.containsKey("sync_state"))
        assertEquals("FULL_SCREEN", (json["importance"] as JsonPrimitive).content)
        val back = EntityJson.toTask(JsonObject(json + ("server_updated_at" to JsonPrimitive("2026-11-06T11:07:33.000001+00:00"))))
        assertEquals(task.copy(syncState = SyncState.CLEAN, serverUpdatedAt = back.serverUpdatedAt), back)
    }

    @Test
    public fun birthdayEventRoundTripsWithDatesAndUnknownYear() {
        val event = EventEntity(
            "1e0d6a1c-0000-4000-8000-000000000002", "Asha", EventType.BIRTHDAY, true, null, null, null,
            LocalDate.of(2000, 2, 29), null, null, null, null, "Family", "YEARLY:2:29:1", Importance.NORMAL, 1L, 2L, null, SyncState.DIRTY, null,
        )
        assertEquals(event.copy(syncState = SyncState.CLEAN), EntityJson.toEvent(EntityJson.event(event)))
    }

    @Test
    public fun pulledReminderKeepsThePhonesFireState() {
        val local = ReminderEntity(
            "1e0d6a1c-0000-4000-8000-000000000003", ReminderItemType.TASK, "t", 0, Importance.NORMAL, ReminderState.FIRED,
            1L, 2L, null, SyncState.CLEAN, null, scheduledFireAt = 500L, lastFiredFireAt = 400L, snoozeCount = 1, snoozedUntil = 450L,
        )
        val serverEdit = EntityJson.reminder(local.copy(offsetMinutes = 60, updatedAt = 3L))
        val merged = EntityJson.toReminder(serverEdit, local)
        assertEquals(60, merged.offsetMinutes)
        assertEquals(ReminderState.FIRED, merged.state)
        assertEquals(400L, merged.lastFiredFireAt)
        assertEquals(500L, merged.scheduledFireAt)
        assertEquals(ReminderState.SCHEDULED, EntityJson.toReminder(serverEdit, null).state)
        assertFalse(serverEdit.containsKey("state"))
    }
}
