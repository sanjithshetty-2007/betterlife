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
import com.sanjith.betterlife.domain.time.today
import com.sanjith.betterlife.data.alarm.PendingAlarmStore
import com.sanjith.betterlife.data.backup.BackupEngine
import com.sanjith.betterlife.data.backup.BackupStatusStore
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.IntegrityChecker
import com.sanjith.betterlife.data.debug.SeedDataGenerator
import com.sanjith.betterlife.data.repository.EventRepository
import com.sanjith.betterlife.data.repository.ProjectRepository
import com.sanjith.betterlife.data.repository.ReminderLogRepository
import com.sanjith.betterlife.data.repository.ReminderRepository
import com.sanjith.betterlife.data.repository.StandaloneReminderRepository
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.data.settings.CalmStore
import com.sanjith.betterlife.data.settings.EventCategoryStore
import com.sanjith.betterlife.data.settings.SettingsRepository
import com.sanjith.betterlife.data.settings.TopTasksStore
import com.sanjith.betterlife.feature.events.EventService
import com.sanjith.betterlife.feature.tasks.TaskService
import com.sanjith.betterlife.reminders.daily.DailyPrompts
import com.sanjith.betterlife.reminders.engine.RescheduleTrigger
import com.sanjith.betterlife.data.setup.DeviceCapabilityChecker
import com.sanjith.betterlife.data.setup.HealthStore
import com.sanjith.betterlife.feature.setup.HealthTests
import com.sanjith.betterlife.data.setup.ManualCheckStore
import com.sanjith.betterlife.data.setup.SetupRepository
import com.sanjith.betterlife.reminders.alarm.TestAlarmController
import com.sanjith.betterlife.reminders.alarm.TestAlarmScheduler
import com.sanjith.betterlife.reminders.engine.AndroidAlarmScheduler
import com.sanjith.betterlife.reminders.engine.AndroidDeviceState
import com.sanjith.betterlife.reminders.engine.AndroidReminderPresenter
import com.sanjith.betterlife.reminders.engine.ReminderEngine
import com.sanjith.betterlife.reminders.engine.RoomReminderStore
import com.sanjith.betterlife.reminders.popup.AndroidPopupActions
import com.sanjith.betterlife.reminders.popup.AndroidSoundPlayer
import com.sanjith.betterlife.reminders.popup.PopupCoordinator
import com.sanjith.betterlife.reminders.popup.RoomItemWatcher
import com.sanjith.betterlife.sync.AccountRepository
import com.sanjith.betterlife.sync.createSupabaseOrNull
import io.github.jan.supabase.SupabaseClient
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

    /** Real phone time, unaffected by the Time Machine: for real alarms and debug timestamps. */
    public val realClock: Clock = SystemClock()

    /** Present only in debug builds, so the Time Machine cannot exist in a release build. */
    public val timeMachine: TimeMachineClock? = if (BuildConfig.DEBUG) TimeMachineClock(realClock) else null

    /** "Now" for all app logic. The Time Machine can shift it in debug builds. */
    public val clock: Clock = timeMachine ?: realClock

    private val nowMillis: () -> Long = { clock.millis() }

    // Real wall time for things the Time Machine must not move: debug log stamps and real alarms.
    private val realNowMillis: () -> Long = { realClock.millis() }

    public val debugLog: DebugLog = DebugLog(
        file = File(appContext.filesDir, DEBUG_LOG_FILE),
        nowMillis = realNowMillis,
        scope = appScope,
        ioDispatcher = ioDispatcher.limitedParallelism(1),
    )

    /** Reminder Health's last-check time and test reminders. */
    public val healthStore: HealthStore = HealthStore(appContext.settingsDataStore)

    public val setupRepository: SetupRepository = SetupRepository(
        checker = DeviceCapabilityChecker(appContext),
        manualChecks = ManualCheckStore(appContext.settingsDataStore),
        healthStore = healthStore,
        ioDispatcher = ioDispatcher,
        nowMillis = realNowMillis,
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

    public val standaloneReminderRepository: StandaloneReminderRepository by lazy {
        StandaloneReminderRepository(database, nowMillis, newId)
    }

    /** Event and "remind me" actions plus the alarm reschedule after each. */
    public val eventService: EventService by lazy {
        EventService(eventRepository, standaloneReminderRepository) { reminderEngine.rescheduleAll(RescheduleTrigger.MANUAL) }
    }

    /** Reminder Health's two test reminders; on the real clock like every real alarm. */
    public val healthTests: HealthTests by lazy { HealthTests(eventService, standaloneReminderRepository, healthStore, realClock) }

    /** The owner's colour categories for events. */
    public val eventCategoryStore: EventCategoryStore = EventCategoryStore(appContext.settingsDataStore)

    public val projectRepository: ProjectRepository by lazy { ProjectRepository(database, nowMillis, newId) }

    /** Manual export and import of the whole database (Prompt 13), on real time. */
    public val backupEngine: BackupEngine by lazy {
        BackupEngine(
            database = { database },
            databaseFile = appContext.getDatabasePath(BetterLifeDatabase.FILE_NAME),
            workDir = appContext.cacheDir,
            safetyDir = File(appContext.filesDir, SAFETY_BACKUP_DIR),
            appVersion = BuildConfig.VERSION_NAME,
            nowMillis = realNowMillis,
            ioDispatcher = ioDispatcher,
        )
    }

    public val backupStatusStore: BackupStatusStore = BackupStatusStore(appContext.settingsDataStore)

    /** Debug integrity check for references SQLite cannot enforce (Prompt 13). */
    public val integrityChecker: IntegrityChecker by lazy { IntegrityChecker(database, nowMillis) }

    /**
     * The Supabase client (Prompt S1), created on first use and only when this build has Supabase
     * settings. Nothing in the reminder path touches it: the app and its alarms work with no network.
     */
    public val supabase: SupabaseClient? by lazy {
        createSupabaseOrNull(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)
    }

    /** Email + password sign-in; the session is kept on the phone and refreshed by Supabase. */
    public val accountRepository: AccountRepository by lazy { AccountRepository(supabase, appScope) }

    /** Pause, digest time and fatigue counters (Prompt 12), on disk. */
    public val calmStore: CalmStore = CalmStore(appContext.settingsDataStore)

    public val topTasksStore: TopTasksStore = TopTasksStore(appContext.settingsDataStore)

    /** Task actions plus the alarm reschedule after each (screens use this, not the repository). */
    public val taskService: TaskService by lazy {
        TaskService(taskRepository, projectRepository, clock, { settingsRepository.current().reminderDefaults }) {
            reminderEngine.rescheduleAll(RescheduleTrigger.MANUAL)
        }
    }

    /** Morning check-in and evening wrap-up nudges, on the real clock like all real alarms. */
    public val dailyPrompts: DailyPrompts by lazy { DailyPrompts(appContext, settingsRepository, realClock) }

    /** Debug screens only. */
    public val seedDataGenerator: SeedDataGenerator by lazy { SeedDataGenerator(database, nowMillis, newId) }

    public val pendingAlarmStore: PendingAlarmStore = PendingAlarmStore(appContext.settingsDataStore)

    public val testAlarmController: TestAlarmController = TestAlarmController(
        scheduler = TestAlarmScheduler(appContext),
        store = pendingAlarmStore,
        log = debugLog,
        nowMillis = realNowMillis,
    )

    /**
     * The reminder engine runs on the REAL clock, never the Time Machine: shifting app time must not
     * mark reminders as missed or move real alarms. Its log rows use real time for the same reason.
     */
    public val reminderEngine: ReminderEngine by lazy {
        val realLog = ReminderLogRepository(database, realNowMillis, newId)
        ReminderEngine(
            // Done on a pop-up completes the task like the Tasks screen does (next instance included).
            store = RoomReminderStore(database, realClock) { taskId -> taskRepository.completeWithNext(taskId, clock.today()) },
            alarms = AndroidAlarmScheduler(appContext),
            device = AndroidDeviceState(appContext),
            presenter = AndroidReminderPresenter(appContext, debugLog, { popupCoordinator }, realClock),
            log = { id, status, level, reason, detail, at ->
                realLog.append(id, status, level, reason, detail, at.toEpochMilli())
            },
            clock = realClock,
            allDayFireTime = { settingsRepository.current().birthdayFireTime },
            calmStore = calmStore,
            settings = { settingsRepository.current() },
        )
    }

    /**
     * The one pop-up stack for the whole app. Runs on the main thread (one thread, so its state never
     * races) and on the real clock. It and the engine refer to each other, hence the lazy lambdas.
     */
    public val popupCoordinator: PopupCoordinator by lazy {
        PopupCoordinator(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            actions = AndroidPopupActions(appContext) { reminderEngine },
            sound = AndroidSoundPlayer(appContext),
            watcher = RoomItemWatcher(database),
            clock = realClock,
            timeoutMinutes = { settingsRepository.current().popupTimeoutMinutes },
        )
    }

    private companion object {
        const val DEBUG_LOG_FILE = "debug_log.txt"
        const val SAFETY_BACKUP_DIR = "safety-backups"
    }
}
