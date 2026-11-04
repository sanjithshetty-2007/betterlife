package com.sanjith.betterlife.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sanjith.betterlife.domain.event.EventCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The owner's event colour categories, kept in Preferences DataStore. */
public class EventCategoryStore(private val dataStore: DataStore<Preferences>) {

    public val categories: Flow<List<EventCategory>> = dataStore.data.map { EventCategory.decode(it[KEY]) }

    /** Adds a category, or changes the colour of the one with the same name. */
    public suspend fun save(category: EventCategory) {
        dataStore.edit { prefs -> prefs[KEY] = EventCategory.encode(EventCategory.upsert(EventCategory.decode(prefs[KEY]), category)) }
    }

    private companion object {
        val KEY = stringPreferencesKey("event_color_categories")
    }
}
