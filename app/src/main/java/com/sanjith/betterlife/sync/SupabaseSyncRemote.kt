package com.sanjith.betterlife.sync

import android.util.Log
import com.sanjith.betterlife.domain.sync.SyncError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import java.io.IOException

/** [SyncRemote] on supabase-kt. Every failure becomes a [SyncFailure] with a reason the screens can explain. */
public class SupabaseSyncRemote(private val client: SupabaseClient) : SyncRemote {

    override suspend fun signedInUserId(): String? = call {
        // After a cold start the saved session loads asynchronously; wait for it before deciding.
        val status = client.auth.sessionStatus.first { it !is SessionStatus.Initializing }
        if (status is SessionStatus.RefreshFailure) throw SyncFailure(SyncError.OFFLINE)
        client.auth.currentUserOrNull()?.id
    }

    override suspend fun upsert(table: String, rows: List<JsonObject>) {
        call { client.from(table).upsert(rows) { onConflict = ID } }
    }

    override suspend fun pull(table: String, fromMicros: Long?, limit: Int): List<JsonObject> = call {
        client.from(table).select {
            if (fromMicros != null) filter { gte(SERVER_UPDATED_AT, EntityJson.cursorText(fromMicros)) }
            order(SERVER_UPDATED_AT, Order.ASCENDING)
            order(ID, Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList<JsonObject>()
    }

    override suspend fun deleteMyData() {
        call { client.postgrest.rpc(DELETE_FUNCTION) }
    }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: SyncFailure) {
        throw e
    } catch (e: RestException) {
        // Only the code is logged, never row contents (agent.md section 3).
        Log.w(TAG, "Supabase refused: ${e.statusCode} ${e.error}")
        throw SyncFailure(errorFor(e.statusCode), rowRejected = e.statusCode in ROW_ERRORS, cause = e)
    } catch (e: HttpRequestException) {
        throw SyncFailure(SyncError.OFFLINE, cause = e)
    } catch (e: HttpRequestTimeoutException) {
        throw SyncFailure(SyncError.OFFLINE, cause = e)
    } catch (e: IOException) {
        throw SyncFailure(SyncError.OFFLINE, cause = e)
    }

    private fun errorFor(status: Int): SyncError = when (status) {
        UNAUTHORIZED, FORBIDDEN -> SyncError.SESSION_EXPIRED
        // A paused free project answers 540 (Supabase) or 503 while it is not running.
        PAUSED, UNAVAILABLE -> SyncError.SERVER_PAUSED
        in ROW_ERRORS -> SyncError.PARTLY_FAILED
        else -> SyncError.UNKNOWN
    }

    private companion object {
        const val TAG = "Sync"
        const val ID = "id"
        const val SERVER_UPDATED_AT = "server_updated_at"
        const val DELETE_FUNCTION = "delete_my_cloud_data"
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val UNAVAILABLE = 503
        const val PAUSED = 540
        val ROW_ERRORS = setOf(400, 409, 422)
    }
}
