package com.sanjith.betterlife.domain.sync

import java.time.Instant

/** What sync needs to know about one row on either side: when it was last changed and whether it is deleted. */
public data class RowMeta(val id: String, val updatedAt: Long, val deletedAt: Long?) {
    val isTombstone: Boolean get() = deletedAt != null
}

public enum class Resolution { TAKE_REMOTE, KEEP_LOCAL }

/**
 * Last write wins on the whole row by the phone's updatedAt (context.md 11a). On a tie a tombstone
 * wins, so a delete is never undone by an equal-time edit. A row the phone does not have is taken.
 *
 * Example: local updatedAt 100, remote 200 -> TAKE_REMOTE; local 100 deleted, remote 100 live -> KEEP_LOCAL.
 */
public object ConflictResolver {
    public fun resolve(local: RowMeta?, remote: RowMeta): Resolution = when {
        local == null -> Resolution.TAKE_REMOTE
        remote.updatedAt > local.updatedAt -> Resolution.TAKE_REMOTE
        remote.updatedAt < local.updatedAt -> Resolution.KEEP_LOCAL
        remote.isTombstone && !local.isTombstone -> Resolution.TAKE_REMOTE
        else -> Resolution.KEEP_LOCAL
    }
}

/** Which pulled rows to write locally, and the cursor to store after them. */
public data class PullPlan(val apply: List<String>, val nextCursorMicros: Long?)

public object SyncPlanner {

    /**
     * For one pulled page (already ordered by server time): the ids to apply and the new cursor (the
     * last row's server time). Rows the phone already has at the same or a newer version are skipped,
     * so a row this phone pushed comes back as a no-op and is never pushed again.
     */
    public fun planPull(remote: List<Pair<RowMeta, Long>>, local: Map<String, RowMeta>): PullPlan = PullPlan(
        apply = remote.filter { (meta, _) -> ConflictResolver.resolve(local[meta.id], meta) == Resolution.TAKE_REMOTE }.map { it.first.id },
        nextCursorMicros = remote.maxOfOrNull { it.second },
    )

    /** True when a full page came back, so there may be more rows to pull. */
    public fun morePages(pageSize: Int, received: Int): Boolean = received >= pageSize
}

/**
 * The pull cursor is the server's timestamptz in microseconds since the epoch (Postgres keeps
 * microseconds; milliseconds would re-read or skip rows saved in the same millisecond).
 */
public object SyncCursor {
    private const val MICROS_PER_SECOND = 1_000_000L
    private const val NANOS_PER_MICRO = 1_000L

    public fun toMicros(instant: Instant): Long = instant.epochSecond * MICROS_PER_SECOND + instant.nano / NANOS_PER_MICRO

    public fun fromMicros(micros: Long): Instant =
        Instant.ofEpochSecond(Math.floorDiv(micros, MICROS_PER_SECOND), Math.floorMod(micros, MICROS_PER_SECOND) * NANOS_PER_MICRO)
}

/** Why the last sync did not finish, in terms the screens can explain (Prompt S2). */
public enum class SyncError { OFFLINE, SIGNED_OUT, SESSION_EXPIRED, SERVER_PAUSED, PARTLY_FAILED, UNKNOWN }

/** The small sync indicator (Home now, Today in Prompt 16): never blocks anything. */
public enum class SyncIndicator { SYNCED, PENDING, OFFLINE, SIGNED_OUT, SERVER_PAUSED, ERROR, OFF }

public object SyncStatusRules {

    /**
     * Example: signed in, nothing pending, last sync ok -> SYNCED; 3 changes waiting and the last
     * attempt was offline -> OFFLINE.
     */
    public fun indicator(configured: Boolean, signedIn: Boolean, pending: Int, lastError: SyncError?): SyncIndicator = when {
        !configured -> SyncIndicator.OFF
        !signedIn -> SyncIndicator.SIGNED_OUT
        lastError == SyncError.OFFLINE -> SyncIndicator.OFFLINE
        lastError == SyncError.SERVER_PAUSED -> SyncIndicator.SERVER_PAUSED
        lastError != null -> SyncIndicator.ERROR
        pending > 0 -> SyncIndicator.PENDING
        else -> SyncIndicator.SYNCED
    }
}
