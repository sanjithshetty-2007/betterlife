package com.sanjith.betterlife.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** The morning check-in's "top 3" for one day. Picks from another day are simply ignored. */
public class TopTasksStore(private val dataStore: DataStore<Preferences>) {

    /** Today's top task ids; empty if none were picked for [today]. */
    public fun observe(today: () -> LocalDate): Flow<Set<String>> = dataStore.data.map { prefs ->
        if (prefs[DATE_KEY] == today().toString()) prefs[IDS_KEY].orEmpty() else emptySet()
    }

    public suspend fun save(date: LocalDate, ids: Set<String>) {
        require(ids.size <= MAX_TOP) { "At most $MAX_TOP top tasks" }
        dataStore.edit {
            it[DATE_KEY] = date.toString()
            it[IDS_KEY] = ids
        }
    }

    public companion object {
        public const val MAX_TOP: Int = 3
        private val DATE_KEY = stringPreferencesKey("top_tasks_date")
        private val IDS_KEY = stringSetPreferencesKey("top_tasks_ids")
    }
}
