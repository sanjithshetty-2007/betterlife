package com.sanjith.betterlife.sync

import com.sanjith.betterlife.domain.sync.RowMeta
import com.sanjith.betterlife.domain.sync.SyncCursor
import com.sanjith.betterlife.domain.sync.SyncError
import com.sanjith.betterlife.domain.time.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** The sync engine against a fake Supabase and a fake phone database (Prompt S2). */
public class SyncEngineTest {

    private class FixedClock(override val zone: ZoneId = ZoneId.of("Asia/Kolkata")) : Clock {
        override fun now(): Instant = Instant.parse("2026-11-06T12:00:00Z")
    }

    /** Server rows with server_updated_at set from a counter, like clock_timestamp(). */
    private class FakeRemote : SyncRemote {
        val tables = mutableMapOf<String, MutableMap<String, JsonObject>>()
        var user: String? = "owner"
        var offline = false
        var rejectIds = setOf<String>()
        var micros = 1_000_000L
        var beforeUpsert: () -> Unit = {}
        var upsertCalls = 0

        override suspend fun signedInUserId(): String? {
            if (offline) throw SyncFailure(SyncError.OFFLINE)
            return user
        }

        override suspend fun upsert(table: String, rows: List<JsonObject>) {
            if (offline) throw SyncFailure(SyncError.OFFLINE)
            upsertCalls++
            beforeUpsert()
            if (rows.any { EntityJson.meta(it).id in rejectIds }) throw SyncFailure(SyncError.PARTLY_FAILED, rowRejected = true)
            rows.forEach { serverWrite(table, it) }
        }

        fun serverWrite(table: String, row: JsonObject) {
            micros += 1
            val stamped = JsonObject(row + ("server_updated_at" to JsonPrimitive(SyncCursor.fromMicros(micros).toString())))
            tables.getOrPut(table) { linkedMapOf() }[EntityJson.meta(row).id] = stamped
        }

        override suspend fun pull(table: String, fromMicros: Long?, limit: Int): List<JsonObject> {
            if (offline) throw SyncFailure(SyncError.OFFLINE)
            return tables[table].orEmpty().values
                .sortedBy { EntityJson.serverMicros(it) }
                .filter { fromMicros == null || EntityJson.serverMicros(it)!! >= fromMicros }
                .take(limit)
        }

        override suspend fun deleteMyData() {
            tables.clear()
        }
    }

    /** The phone database: rows as JSON plus a dirty flag, like syncState. */
    private class FakeLocal : LocalSyncStore {
        val rows = mutableMapOf<String, MutableMap<String, Pair<JsonObject, Boolean>>>()
        val cursors = mutableMapOf<String, Long>()
        var everythingDirtyCalls = 0

        fun write(table: String, row: JsonObject, dirty: Boolean = true) {
            rows.getOrPut(table) { linkedMapOf() }[EntityJson.meta(row).id] = row to dirty
        }

        fun row(table: String, id: String): JsonObject? = rows[table]?.get(id)?.first
        fun isDirty(table: String, id: String): Boolean = rows[table]?.get(id)?.second == true

        override suspend fun dirty(table: String, limit: Int) = rows[table].orEmpty().values.filter { it.second }.map { it.first }.take(limit)

        override suspend fun markClean(table: String, id: String, updatedAt: Long): Boolean {
            val (row, dirty) = rows[table]?.get(id) ?: return false
            if (!dirty || EntityJson.meta(row).updatedAt != updatedAt) return false
            rows.getValue(table)[id] = row to false
            return true
        }

        override suspend fun metas(table: String, ids: List<String>): Map<String, RowMeta> =
            ids.mapNotNull { id -> rows[table]?.get(id)?.first?.let { id to EntityJson.meta(it) } }.toMap()

        override suspend fun applyRemote(table: String, rows: List<JsonObject>) {
            rows.forEach { write(table, it, dirty = false) }
        }

        override suspend fun cursor(table: String): Long? = cursors[table]
        override suspend fun setCursor(table: String, micros: Long) {
            cursors[table] = micros
        }

        override suspend fun markEverythingDirtyAndResetCursors() {
            everythingDirtyCalls++
            rows.values.forEach { table -> table.replaceAll { _, v -> v.first to true } }
            cursors.clear()
        }
    }

    private val remote = FakeRemote()
    private val local = FakeLocal()
    private val status = InMemorySyncStatusStore(SyncStatus(lastUserId = "owner"))
    private var reschedules = 0
    private val engine = SyncEngine(remote, local, status, FixedClock()) { reschedules++ }

    private fun task(id: String, updatedAt: Long, title: String = "Task $id", deletedAt: Long? = null) = buildJsonObject {
        put("id", id)
        put("title", title)
        put("updated_at", updatedAt)
        put("deleted_at", deletedAt)
    }

    private fun title(row: JsonObject?): String? = (row?.get("title") as? JsonPrimitive)?.content

    @Test
    public fun dirtyRowsArePushedAndMarkedClean() = runTest {
        local.write(SyncTables.TASKS, task("a", 10))
        local.write(SyncTables.TASKS, task("b", 20))

        val result = engine.sync()

        assertEquals(2, result.pushed)
        assertNull(result.error)
        assertFalse(local.isDirty(SyncTables.TASKS, "a"))
        assertEquals(setOf("a", "b"), remote.tables.getValue(SyncTables.TASKS).keys)
        assertEquals(Instant.parse("2026-11-06T12:00:00Z"), status.read().lastSuccessAt)
    }

    @Test
    public fun newServerRowsArePulledCleanAndTheCursorMoves() = runTest {
        remote.serverWrite(SyncTables.TASKS, task("x", 50, "From the cloud"))

        val result = engine.sync()

        assertEquals(1, result.pulled)
        assertEquals("From the cloud", title(local.row(SyncTables.TASKS, "x")))
        assertFalse(local.isDirty(SyncTables.TASKS, "x"))
        assertEquals(remote.micros, local.cursors[SyncTables.TASKS])
    }

    @Test
    public fun pulledRowsAreNotPushedBackAndAPushedRowDoesNotComeBackAsAChange() = runTest {
        remote.serverWrite(SyncTables.TASKS, task("x", 50))
        local.write(SyncTables.TASKS, task("mine", 60))
        engine.sync()
        val calls = remote.upsertCalls

        val second = engine.sync()

        assertEquals(0, second.pushed)
        assertEquals(0, second.pulled)
        assertEquals(calls, remote.upsertCalls)
    }

    @Test
    public fun theNewerEditWinsInBothDirections() = runTest {
        // Server newer than the phone: the server's version is taken.
        local.write(SyncTables.TASKS, task("a", 10, "old phone"), dirty = false)
        remote.serverWrite(SyncTables.TASKS, task("a", 20, "newer cloud"))
        // Phone newer than the server (edited offline): the phone's version wins and is pushed.
        remote.serverWrite(SyncTables.TASKS, task("b", 10, "old cloud"))
        local.write(SyncTables.TASKS, task("b", 30, "newer phone"))

        engine.sync()

        assertEquals("newer cloud", title(local.row(SyncTables.TASKS, "a")))
        assertEquals("newer phone", title(local.row(SyncTables.TASKS, "b")))
        assertEquals("newer phone", title(remote.tables.getValue(SyncTables.TASKS).getValue("b")))
    }

    @Test
    public fun aTombstoneWinsAnEqualTimeEdit() = runTest {
        local.write(SyncTables.TASKS, task("a", 10, "edited"), dirty = false)
        remote.serverWrite(SyncTables.TASKS, task("a", 10, "edited", deletedAt = 10))

        engine.sync()

        assertEquals(10L, EntityJson.meta(local.row(SyncTables.TASKS, "a")!!).deletedAt)
    }

    @Test
    public fun theCursorAdvancesAcrossSeveralPages() = runTest {
        repeat(SyncEngine.PULL_PAGE + 20) { remote.serverWrite(SyncTables.EVENTS, task("e$it", 5)) }

        val result = engine.sync()

        assertEquals(SyncEngine.PULL_PAGE + 20, result.pulled)
        assertEquals(remote.micros, local.cursors[SyncTables.EVENTS])
        assertEquals(0, engine.sync().pulled)
    }

    @Test
    public fun anEditDuringThePushStaysDirtyAndGoesUpNextTime() = runTest {
        local.write(SyncTables.TASKS, task("a", 10, "first"))
        // The owner edits the task while the upload is on its way.
        remote.beforeUpsert = {
            local.write(SyncTables.TASKS, task("a", 11, "second"))
            remote.beforeUpsert = {}
        }

        engine.sync()
        assertTrue(local.isDirty(SyncTables.TASKS, "a"))

        engine.sync()
        assertFalse(local.isDirty(SyncTables.TASKS, "a"))
        assertEquals("second", title(remote.tables.getValue(SyncTables.TASKS).getValue("a")))
    }

    @Test
    public fun firstSignInPushesEverythingLocalThenPullsAndLosesNothing() = runTest {
        status.update { it.copy(lastUserId = null) }
        local.write(SyncTables.TASKS, task("phone-only", 10), dirty = false) // CLEAN from an earlier account
        remote.serverWrite(SyncTables.TASKS, task("cloud-only", 20))

        engine.sync()

        assertEquals(1, local.everythingDirtyCalls)
        assertEquals("owner", status.read().lastUserId)
        assertTrue(remote.tables.getValue(SyncTables.TASKS).containsKey("phone-only"))
        assertTrue(local.row(SyncTables.TASKS, "cloud-only") != null)
    }

    @Test
    public fun offlineKeepsEverythingAndTheNextTryWorks() = runTest {
        local.write(SyncTables.TASKS, task("a", 10))
        remote.offline = true

        val failed = engine.sync()
        assertEquals(SyncError.OFFLINE, failed.error)
        assertTrue(local.isDirty(SyncTables.TASKS, "a"))
        assertEquals(SyncError.OFFLINE, status.read().lastError)

        remote.offline = false
        val ok = engine.sync()
        assertNull(ok.error)
        assertFalse(local.isDirty(SyncTables.TASKS, "a"))
        assertNull(status.read().lastError)
    }

    @Test
    public fun oneRowTheServerRefusesDoesNotBlockTheOthers() = runTest {
        local.write(SyncTables.TASKS, task("good", 10))
        local.write(SyncTables.TASKS, task("bad", 10))
        remote.rejectIds = setOf("bad")

        val result = engine.sync()

        assertEquals(SyncError.PARTLY_FAILED, result.error)
        assertEquals(1, result.pushed)
        assertEquals(1, result.rejected)
        assertFalse(local.isDirty(SyncTables.TASKS, "good"))
        assertTrue(local.isDirty(SyncTables.TASKS, "bad"))
    }

    @Test
    public fun pulledChangesRescheduleRemindersExactlyOnce() = runTest {
        remote.serverWrite(SyncTables.TASKS, task("t", 1))
        remote.serverWrite(SyncTables.REMINDERS, task("r1", 1))
        remote.serverWrite(SyncTables.REMINDERS, task("r2", 1))

        engine.sync()
        assertEquals(1, reschedules)

        engine.sync() // nothing new
        assertEquals(1, reschedules)
    }

    @Test
    public fun signedOutDoesNothing() = runTest {
        remote.user = null
        local.write(SyncTables.TASKS, task("a", 10))

        val result = engine.sync()

        assertEquals(SyncError.SIGNED_OUT, result.error)
        assertEquals(0, remote.upsertCalls)
        assertTrue(local.isDirty(SyncTables.TASKS, "a"))
    }

    @Test
    public fun deletingCloudDataLeavesThePhoneAndMarksItForALaterUpload() = runTest {
        local.write(SyncTables.TASKS, task("a", 10))
        engine.sync()

        engine.deleteCloudData()

        assertTrue(remote.tables.isEmpty())
        assertTrue(local.row(SyncTables.TASKS, "a") != null)
        assertTrue(local.isDirty(SyncTables.TASKS, "a"))
        assertNull(status.read().lastUserId)
    }
}
