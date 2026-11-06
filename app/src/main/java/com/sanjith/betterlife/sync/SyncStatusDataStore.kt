package com.sanjith.betterlife.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sanjith.betterlife.domain.sync.SyncError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

/** [SyncStatusStore] in Preferences DataStore (local only). */
public class SyncStatusDataStore(private val dataStore: DataStore<Preferences>) : SyncStatusStore {

    public val status: Flow<SyncStatus> = dataStore.data.map(::read)

    override suspend fun read(): SyncStatus = status.first()

    override suspend fun update(change: (SyncStatus) -> SyncStatus) {
        dataStore.edit { prefs -> write(prefs, change(read(prefs))) }
    }

    private fun read(p: Preferences) = SyncStatus(
        lastSuccessAt = p[LAST_SUCCESS]?.let(Instant::ofEpochMilli),
        lastAttemptAt = p[LAST_ATTEMPT]?.let(Instant::ofEpochMilli),
        lastError = p[LAST_ERROR]?.let { name -> SyncError.entries.firstOrNull { it.name == name } },
        lastUserId = p[LAST_USER],
    )

    private fun write(p: MutablePreferences, s: SyncStatus) {
        s.lastSuccessAt?.let { p[LAST_SUCCESS] = it.toEpochMilli() } ?: p.remove(LAST_SUCCESS)
        s.lastAttemptAt?.let { p[LAST_ATTEMPT] = it.toEpochMilli() } ?: p.remove(LAST_ATTEMPT)
        s.lastError?.let { p[LAST_ERROR] = it.name } ?: p.remove(LAST_ERROR)
        s.lastUserId?.let { p[LAST_USER] = it } ?: p.remove(LAST_USER)
    }

    private companion object {
        val LAST_SUCCESS = longPreferencesKey("sync_last_success_at")
        val LAST_ATTEMPT = longPreferencesKey("sync_last_attempt_at")
        val LAST_ERROR = stringPreferencesKey("sync_last_error")
        val LAST_USER = stringPreferencesKey("sync_last_user_id")
    }
}
