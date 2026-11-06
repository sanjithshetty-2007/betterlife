package com.sanjith.betterlife.sync

import com.sanjith.betterlife.domain.sync.RowMeta
import com.sanjith.betterlife.domain.sync.SyncError
import com.sanjith.betterlife.domain.sync.SyncPlanner
import com.sanjith.betterlife.domain.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import java.time.Instant

// The sync engine talks to Supabase and Room only through these ports, so all of its logic runs in
// fast PC tests with fakes (Prompt S2 tests).

/** A request to Supabase failed. [rowRejected] means the server refused this data (not the connection). */
public class SyncFailure(public val error: SyncError, public val rowRejected: Boolean = false, cause: Throwable? = null) :
    Exception(error.name, cause)

public interface SyncRemote {
    /** The signed-in user's id, or null when signed out. Waits for a saved session to load. */
    public suspend fun signedInUserId(): String?

    /** Inserts or replaces [rows] by id. Throws [SyncFailure]. */
    public suspend fun upsert(table: String, rows: List<JsonObject>)

    /** Rows with server_updated_at at or after [fromMicros] (all when null), oldest first, at most [limit]. */
    public suspend fun pull(table: String, fromMicros: Long?, limit: Int): List<JsonObject>

    /** "Delete my cloud data": removes the signed-in user's rows on the server only. */
    public suspend fun deleteMyData()
}

public interface LocalSyncStore {
    /** Rows changed on the phone and not yet pushed (debug seed rows are never included). */
    public suspend fun dirty(table: String, limit: Int): List<JsonObject>

    /** Marks a pushed row CLEAN, but only if it was not edited again meanwhile; true if it was marked. */
    public suspend fun markClean(table: String, id: String, updatedAt: Long): Boolean

    public suspend fun metas(table: String, ids: List<String>): Map<String, RowMeta>

    /** Writes server rows in one transaction, CLEAN, keeping local-only columns. */
    public suspend fun applyRemote(table: String, rows: List<JsonObject>)

    public suspend fun cursor(table: String): Long?
    public suspend fun setCursor(table: String, micros: Long)

    /** A different account signed in, or a restore: push everything again and pull from the start. */
    public suspend fun markEverythingDirtyAndResetCursors()
}

/** Last sync time, last error and which account was last synced (kept on disk). */
public data class SyncStatus(
    val lastSuccessAt: Instant? = null,
    val lastAttemptAt: Instant? = null,
    val lastError: SyncError? = null,
    val lastUserId: String? = null,
)

public interface SyncStatusStore {
    public suspend fun read(): SyncStatus
    public suspend fun update(change: (SyncStatus) -> SyncStatus)
}

public class InMemorySyncStatusStore(private var status: SyncStatus = SyncStatus()) : SyncStatusStore {
    override suspend fun read(): SyncStatus = status
    override suspend fun update(change: (SyncStatus) -> SyncStatus) {
        status = change(status)
    }
}

/** What one sync run did (returned for the worker, tests and the debug log). */
public data class SyncResult(val pushed: Int, val pulled: Int, val rejected: Int, val error: SyncError?)

/**
 * Offline-first sync (context.md 11a, Prompt S2). One run: push every DIRTY row in batches (a row is
 * marked CLEAN only if it was not edited during the push), then pull each table from its cursor and
 * write the newer rows CLEAN (last write wins, tombstones win ties). Nothing here runs on the main
 * thread or in a receiver; the worker calls [sync]. [onPulledChanges] runs once after a pull that
 * changed anything (the app reschedules reminders there).
 */
public class SyncEngine(
    private val remote: SyncRemote,
    private val local: LocalSyncStore,
    private val status: SyncStatusStore,
    private val clock: Clock,
    private val onPulledChanges: suspend () -> Unit,
) {
    private val lock = Mutex()

    public suspend fun sync(): SyncResult {
        val result = lock.withLock { syncLocked() }
        if (result.pulled > 0) onPulledChanges()
        return result
    }

    /** Deletes the server copy, then marks everything to be pushed again if the owner signs in later. */
    public suspend fun deleteCloudData() {
        lock.withLock {
            remote.deleteMyData()
            local.markEverythingDirtyAndResetCursors()
            status.update { it.copy(lastUserId = null) }
        }
    }

    private suspend fun syncLocked(): SyncResult {
        val now = clock.now()
        status.update { it.copy(lastAttemptAt = now) }
        val user = try {
            remote.signedInUserId()
        } catch (e: SyncFailure) {
            return finish(SyncResult(0, 0, 0, e.error))
        } ?: return finish(SyncResult(0, 0, 0, SyncError.SIGNED_OUT))
        // First sign-in on this phone (or another account): everything local goes up, then all is pulled.
        if (status.read().lastUserId != user) {
            local.markEverythingDirtyAndResetCursors()
            status.update { it.copy(lastUserId = user) }
        }
        var pushed = 0
        var rejected = 0
        var pulled = 0
        return try {
            SyncTables.ALL.forEach { table ->
                val (ok, bad) = push(table)
                pushed += ok
                rejected += bad
            }
            SyncTables.ALL.forEach { table -> pulled += pull(table) }
            finish(SyncResult(pushed, pulled, rejected, if (rejected > 0) SyncError.PARTLY_FAILED else null))
        } catch (e: SyncFailure) {
            finish(SyncResult(pushed, pulled, rejected, e.error))
        }
    }

    private suspend fun finish(result: SyncResult): SyncResult {
        status.update {
            if (result.error == null) it.copy(lastSuccessAt = clock.now(), lastError = null) else it.copy(lastError = result.error)
        }
        return result
    }

    // Returns (pushed, rejected). A batch the server refuses is retried row by row, so one bad row
    // never blocks the rest; rejected rows stay DIRTY and are reported, never lost.
    private suspend fun push(table: String): Pair<Int, Int> {
        var pushed = 0
        val rejectedIds = mutableSetOf<String>()
        while (true) {
            val batch = local.dirty(table, PUSH_BATCH).filter { EntityJson.meta(it).id !in rejectedIds }
            if (batch.isEmpty()) break
            val accepted = try {
                remote.upsert(table, batch)
                batch
            } catch (e: SyncFailure) {
                if (!e.rowRejected) throw e
                batch.filter { row ->
                    try {
                        remote.upsert(table, listOf(row))
                        true
                    } catch (one: SyncFailure) {
                        if (!one.rowRejected) throw one
                        rejectedIds += EntityJson.meta(row).id
                        false
                    }
                }
            }
            accepted.forEach { row ->
                val meta = EntityJson.meta(row)
                if (local.markClean(table, meta.id, meta.updatedAt)) pushed++
            }
            if (batch.size < PUSH_BATCH) break
        }
        return pushed to rejectedIds.size
    }

    // Returns how many rows were written locally.
    private suspend fun pull(table: String): Int {
        var applied = 0
        var cursor = local.cursor(table)
        while (true) {
            val page = remote.pull(table, cursor, PULL_PAGE)
            if (page.isEmpty()) break
            val metas = page.map { EntityJson.meta(it) to (EntityJson.serverMicros(it) ?: 0L) }
            val plan = SyncPlanner.planPull(metas, local.metas(table, metas.map { it.first.id }))
            if (plan.apply.isNotEmpty()) {
                val ids = plan.apply.toSet()
                local.applyRemote(table, page.filter { EntityJson.meta(it).id in ids })
                applied += plan.apply.size
            }
            val next = plan.nextCursorMicros
            // A full page that does not move the cursor would repeat forever; stop and try next time.
            if (next == null || next == cursor) break
            local.setCursor(table, next)
            cursor = next
            if (!SyncPlanner.morePages(PULL_PAGE, page.size)) break
        }
        return applied
    }

    public companion object {
        public const val PUSH_BATCH: Int = 200
        public const val PULL_PAGE: Int = 500
    }
}
