package com.sanjith.betterlife

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.sanjith.betterlife.core.DebugLog
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.tracker.ProteinTable
import com.sanjith.betterlife.data.tracker.SleepTable
import com.sanjith.betterlife.data.tracker.TrackerRepository
import com.sanjith.betterlife.data.tracker.TrackerService
import com.sanjith.betterlife.data.tracker.TrackerSettingsStore
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.tracker.TrackerKind
import com.sanjith.betterlife.domain.tracker.TrackerRules
import com.sanjith.betterlife.reminders.engine.AndroidDeviceState
import com.sanjith.betterlife.reminders.popup.PopupCoordinator
import com.sanjith.betterlife.reminders.tracker.TrackerReminders
import java.time.LocalDate

/** One tracker's parts: its settings, days, actions and daily reminder. */
public class Tracker(
    public val settingsStore: TrackerSettingsStore,
    public val repository: TrackerRepository,
    public val service: TrackerService,
    public val reminders: TrackerReminders,
)

/**
 * The daily trackers (protein and sleep; owner's features, 2026-11-21), local only. The days use app
 * time like habits; the reminders the REAL clock, like every alarm.
 */
public class Trackers(
    context: Context,
    database: () -> BetterLifeDatabase,
    settingsDataStore: DataStore<Preferences>,
    nowMillis: () -> Long,
    newId: () -> String,
    realClock: Clock,
    rolloverHour: suspend () -> Int,
    habitToday: suspend () -> LocalDate,
    coordinator: () -> PopupCoordinator,
    log: DebugLog,
) {
    private val byKind: Map<TrackerKind, Tracker> = TrackerKind.entries.associateWith { kind ->
        val store = TrackerSettingsStore(settingsDataStore, kind)
        val table = when (kind) {
            TrackerKind.PROTEIN -> ProteinTable { database().proteinDao() }
            TrackerKind.SLEEP -> SleepTable { database().sleepDao() }
        }
        val repository = TrackerRepository(kind, table, nowMillis, newId)
        val question = context.getString(
            when (kind) {
                TrackerKind.PROTEIN -> R.string.protein_popup_question
                TrackerKind.SLEEP -> R.string.sleep_popup_question
            },
        )
        val device = AndroidDeviceState(context)
        val reminders = TrackerReminders(kind, context, store, repository, realClock, rolloverHour, device::callActive, coordinator, question, log)
        val service = TrackerService(repository, store, { TrackerRules.latestDay(kind, habitToday()) }) { reminders.replan() }
        Tracker(store, repository, service, reminders)
    }

    public operator fun get(kind: TrackerKind): Tracker = byKind.getValue(kind)

    /** Re-plans every tracker's alarm (boot, update, time change, app launch). */
    public suspend fun replanAll() {
        byKind.values.forEach { it.reminders.replan() }
    }
}
