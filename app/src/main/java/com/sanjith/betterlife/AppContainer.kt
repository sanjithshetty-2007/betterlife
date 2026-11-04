package com.sanjith.betterlife

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.sanjith.betterlife.core.DebugLog
import com.sanjith.betterlife.data.alarm.PendingAlarmStore
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

    // The injected Clock arrives in Prompt 6; until then the spikes read wall time here only.
    private val nowMillis: () -> Long = System::currentTimeMillis

    public val debugLog: DebugLog = DebugLog(
        file = File(appContext.filesDir, DEBUG_LOG_FILE),
        nowMillis = nowMillis,
        scope = appScope,
        ioDispatcher = ioDispatcher.limitedParallelism(1),
    )

    public val setupRepository: SetupRepository = SetupRepository(
        checker = DeviceCapabilityChecker(appContext),
        manualChecks = ManualCheckStore(appContext.settingsDataStore),
        ioDispatcher = ioDispatcher,
    )

    public val pendingAlarmStore: PendingAlarmStore = PendingAlarmStore(appContext.settingsDataStore)

    public val testAlarmController: TestAlarmController = TestAlarmController(
        scheduler = TestAlarmScheduler(appContext),
        store = pendingAlarmStore,
        log = debugLog,
        nowMillis = nowMillis,
    )

    private companion object {
        const val DEBUG_LOG_FILE = "debug_log.txt"
    }
}
