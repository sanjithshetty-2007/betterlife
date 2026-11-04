package com.sanjith.betterlife

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.sanjith.betterlife.core.DebugLog
import com.sanjith.betterlife.core.SystemClock
import com.sanjith.betterlife.core.TimeMachineClock
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.time.millis
import com.sanjith.betterlife.data.alarm.PendingAlarmStore
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.debug.SeedDataGenerator
import com.sanjith.betterlife.data.repository.EventRepository
import com.sanjith.betterlife.data.repository.ReminderLogRepository
import com.sanjith.betterlife.data.repository.ReminderRepository
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.data.settings.SettingsRepository
import com.sanjith.betterlife.data.setup.DeviceCapabilityChecker
import com.sanjith.betterlife.data.setup.ManualCheckStore
import com.sanjith.betterlife.data.setup.SetupRepository
import com.sanjith.betterlife.reminders.alarm.TestAlarmController
import com.sanjith.betterlife.reminders.alarm.TestAlarmScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.UUID

// One DataStore file per process; the delegate guarantees a single instance.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Holds the shared objects (repositories, clock, scheduler) for manual dependency injection.
 * Later prompts add their dependencies here instead of using Hilt or Koin.
 */
public class AppContainer(appContext: Context) {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /** Lives as long as the process. Receivers use it with goAsync for short work. */
    public val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val systemClock: Clock = SystemClock()

    /** Present only in debug builds, so the Time Machine cannot exist in a release build. */
    public val timeMachine: TimeMachineClock? = if (BuildConfig.DEBUG) TimeMachineClock(systemClock) else null

    /** "Now" for all app logic. The Time Machine can shift it in debug builds. */
    public val clock: Clock = timeMachine ?: systemClock

    private val nowMillis: () -> Long = { clock.millis() }

    // Real wall time for things the Time Machine must not move: debug log stamps and real alarms.
    private val realNowMillis: () -> Long = { systemClock.millis() }

    public val debugLog: DebugLog = DebugLog(
        file = File(appContext.filesDir, DEBUG_LOG_FILE),
        nowMillis = realNowMillis,
        scope = appScope,
        ioDispatcher = ioDispatcher.limitedParallelism(1),
    )

    public val setupRepository: SetupRepository = SetupRepository(
        checker = DeviceCapabilityChecker(appContext),
        manualChecks = ManualCheckStore(appContext.settingsDataStore),
        ioDispatcher = ioDispatcher,
    )

    private val newId: () -> String = { UUID.randomUUID().toString() }

    /** Opened lazily: Room creates or migrates the file on first use, off the main thread. */
    public val database: BetterLifeDatabase by lazy { BetterLifeDatabase.build(appContext) }

    public val settingsRepository: SettingsRepository = SettingsRepository(appContext.settingsDataStore)

    public val taskRepository: TaskRepository by lazy { TaskRepository(database, nowMillis, newId) }
    public val eventRepository: EventRepository by lazy { EventRepository(database, nowMillis, newId) }
    public val reminderRepository: ReminderRepository by lazy { ReminderRepository(database, nowMillis) }
    public val reminderLogRepository: ReminderLogRepository by lazy {
        ReminderLogRepository(database, nowMillis, newId)
    }

    /** Debug screens only. */
    public val seedDataGenerator: SeedDataGenerator by lazy { SeedDataGenerator(database, nowMillis, newId) }

    public val pendingAlarmStore: PendingAlarmStore = PendingAlarmStore(appContext.settingsDataStore)

    public val testAlarmController: TestAlarmController = TestAlarmController(
        scheduler = TestAlarmScheduler(appContext),
        store = pendingAlarmStore,
        log = debugLog,
        nowMillis = realNowMillis,
    )

    private companion object {
        const val DEBUG_LOG_FILE = "debug_log.txt"
    }
}
