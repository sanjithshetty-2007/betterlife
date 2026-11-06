package com.sanjith.betterlife.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Last successful and last failed backup times (context.md 11: show both). Epoch millis, real time. */
public data class BackupStatus(val lastSuccessAt: Long? = null, val lastFailureAt: Long? = null)

public class BackupStatusStore(private val dataStore: DataStore<Preferences>) {

    public val status: Flow<BackupStatus> = dataStore.data.map { BackupStatus(it[LAST_SUCCESS], it[LAST_FAILURE]) }

    public suspend fun success(at: Long) {
        dataStore.edit { it[LAST_SUCCESS] = at }
    }

    public suspend fun failure(at: Long) {
        dataStore.edit { it[LAST_FAILURE] = at }
    }

    private companion object {
        val LAST_SUCCESS = longPreferencesKey("backup_last_success_at")
        val LAST_FAILURE = longPreferencesKey("backup_last_failure_at")
    }
}
