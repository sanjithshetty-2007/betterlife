package com.sanjith.betterlife.data.setup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.sanjith.betterlife.domain.setup.SetupCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Stores the owner's ticks for the Vivo settings the app cannot detect. */
public class ManualCheckStore(private val dataStore: DataStore<Preferences>) {

    public val ticks: Flow<Set<SetupCheck>> = dataStore.data.map { prefs ->
        SetupCheck.MANUAL_CHECKS.filterTo(mutableSetOf()) { prefs[keyFor(it)] == true }
    }

    public suspend fun setTicked(check: SetupCheck, ticked: Boolean) {
        require(check.isManual) { "Only manual checks can be ticked: $check" }
        dataStore.edit { it[keyFor(check)] = ticked }
    }

    // Keys use the enum name, so renaming a SetupCheck constant would forget its tick.
    private fun keyFor(check: SetupCheck): Preferences.Key<Boolean> =
        booleanPreferencesKey("$KEY_PREFIX${check.name}")

    private companion object {
        const val KEY_PREFIX = "setup_manual_"
    }
}
