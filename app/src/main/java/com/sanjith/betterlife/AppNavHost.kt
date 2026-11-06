package com.sanjith.betterlife

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sanjith.betterlife.feature.backup.BackupScreen
import com.sanjith.betterlife.feature.calm.PauseBanner
import com.sanjith.betterlife.feature.calm.PauseSheet
import com.sanjith.betterlife.feature.calm.QuietCalmScreen
import kotlinx.coroutines.delay
import java.time.Instant
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.setup.HealthSummary
import com.sanjith.betterlife.feature.center.ReminderCenterScreen
import com.sanjith.betterlife.feature.setup.rowText
import com.sanjith.betterlife.feature.debug.AlarmSpikeScreen
import com.sanjith.betterlife.feature.debug.DatabaseDebugScreen
import com.sanjith.betterlife.feature.debug.PopupSpikeScreen
import com.sanjith.betterlife.feature.debug.ReminderEngineDebugScreen
import com.sanjith.betterlife.feature.debug.TimeMachineScreen
import com.sanjith.betterlife.feature.events.AgendaScreen
import com.sanjith.betterlife.feature.events.AgendaTarget
import com.sanjith.betterlife.feature.events.EventEditorScreen
import com.sanjith.betterlife.feature.events.EventEditorViewModel
import com.sanjith.betterlife.feature.events.ReminderDefaultsScreen
import com.sanjith.betterlife.feature.events.StandaloneEditorScreen
import com.sanjith.betterlife.feature.events.StandaloneEditorViewModel
import com.sanjith.betterlife.feature.home.DebugTool
import com.sanjith.betterlife.feature.home.HomeScreen
import com.sanjith.betterlife.feature.setup.SetupScreen
import com.sanjith.betterlife.feature.tasks.CheckInScreen
import com.sanjith.betterlife.feature.tasks.ProjectsScreen
import com.sanjith.betterlife.feature.tasks.TaskEditorScreen
import com.sanjith.betterlife.feature.tasks.TaskEditorViewModel
import com.sanjith.betterlife.feature.tasks.TaskListScreen

/** Screen routes. Plain strings with path arguments. */
internal object Routes {
    const val HOME = "home"
    const val SETUP = "setup"
    const val TASKS = "tasks"
    const val TASK_EDITOR = "task"
    const val PROJECTS = "projects"
    const val CHECK_INS = "check-ins"
    const val AGENDA = "agenda"
    const val EVENT_EDITOR = "event"
    const val REMINDER_EDITOR = "remind"
    const val REMINDER_DEFAULTS = "reminder-defaults"
    const val CENTER = "reminder-center"
    const val QUIET = "quiet-calm"
    const val BACKUP = "backup"
    const val POPUP_SPIKE = "debug/popup-spike"
    const val ALARM_SPIKE = "debug/alarm-spike"
    const val DATABASE = "debug/database"
    const val TIME_MACHINE = "debug/time-machine"
    const val ENGINE = "debug/reminder-engine"

    fun taskEditor(id: String?): String = "$TASK_EDITOR/${id ?: TaskEditorViewModel.NEW_TASK}"

    fun eventEditor(id: String?, type: EventType = EventType.EVENT): String =
        "$EVENT_EDITOR/${type.name}/${id ?: EventEditorViewModel.NEW}"

    fun reminderEditor(id: String?): String = "$REMINDER_EDITOR/${id ?: EventEditorViewModel.NEW}"
}

/** All screens of the single Activity. NavHost also handles the system (predictive) back gesture. */
@Composable
internal fun AppNavHost(routeToOpen: String?, onRouteOpened: () -> Unit) {
    val navController = rememberNavController()
    // Another part of the app (a pop-up's Reschedule, a daily nudge) asked to open a screen.
    LaunchedEffect(routeToOpen) {
        val route = routeToOpen ?: return@LaunchedEffect
        if (route != Routes.HOME) navController.navigate(route)
        onRouteOpened()
    }
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            var pauseSheet by remember { mutableStateOf(false) }
            HomeScreen(
                pauseBanner = activePause()?.let { until -> { PauseBanner(until) } },
                onPause = { pauseSheet = true },
                onOpenQuiet = { navController.navigate(Routes.QUIET) },
                onOpenBackup = { navController.navigate(Routes.BACKUP) },
                onOpenCenter = { navController.navigate(Routes.CENTER) },
                healthProblems = healthProblems(),
                onOpenTasks = { navController.navigate(Routes.TASKS) },
                onOpenSetup = { navController.navigate(Routes.SETUP) },
                onOpenAgenda = { navController.navigate(Routes.AGENDA) },
                // Debug tools are unreachable in release builds.
                debugTools = if (BuildConfig.DEBUG) debugTools(navController) else emptyList(),
            )
            if (pauseSheet) PauseSheetHost(onDismiss = { pauseSheet = false })
        }
        composable(Routes.SETUP) { SetupScreen(onBack = back, onOpenCenter = { navController.navigate(Routes.CENTER) }) }
        composable(Routes.QUIET) { QuietCalmScreen(onBack = back) }
        composable(Routes.BACKUP) { BackupScreen(onBack = back) }
        composable(Routes.CENTER) {
            ReminderCenterScreen(
                onBack = back,
                onOpenItem = { type, id ->
                    navController.navigate(
                        when (type) {
                            ReminderItemType.TASK -> Routes.taskEditor(id)
                            ReminderItemType.EVENT -> Routes.eventEditor(id)
                            ReminderItemType.STANDALONE -> Routes.reminderEditor(id)
                        },
                    )
                },
            )
        }
        composable(Routes.TASKS) {
            TaskListScreen(
                onBack = back,
                onOpenTask = { id -> navController.navigate(Routes.taskEditor(id)) },
                onOpenProjects = { navController.navigate(Routes.PROJECTS) },
                onOpenCheckIns = { navController.navigate(Routes.CHECK_INS) },
            )
        }
        composable("${Routes.TASK_EDITOR}/{${TaskEditorViewModel.ARG_TASK_ID}}") { TaskEditorScreen(onClose = back) }
        composable(Routes.PROJECTS) { ProjectsScreen(onBack = back) }
        composable(Routes.CHECK_INS) { CheckInScreen(onBack = back) }
        composable(Routes.AGENDA) {
            AgendaScreen(
                onBack = back,
                onOpen = { target ->
                    navController.navigate(
                        when (target) {
                            is AgendaTarget.Event -> Routes.eventEditor(target.id)
                            is AgendaTarget.Task -> Routes.taskEditor(target.id)
                            is AgendaTarget.Reminder -> Routes.reminderEditor(target.id)
                        },
                    )
                },
                onNewEvent = { type -> navController.navigate(Routes.eventEditor(null, type)) },
                onNewReminder = { navController.navigate(Routes.reminderEditor(null)) },
                onOpenDefaults = { navController.navigate(Routes.REMINDER_DEFAULTS) },
            )
        }
        composable("${Routes.EVENT_EDITOR}/{${EventEditorViewModel.ARG_TYPE}}/{${EventEditorViewModel.ARG_EVENT_ID}}") {
            EventEditorScreen(onClose = back)
        }
        composable("${Routes.REMINDER_EDITOR}/{${StandaloneEditorViewModel.ARG_ID}}") { StandaloneEditorScreen(onClose = back) }
        composable(Routes.REMINDER_DEFAULTS) { ReminderDefaultsScreen(onBack = back) }
        if (BuildConfig.DEBUG) {
            composable(Routes.POPUP_SPIKE) { PopupSpikeScreen(onBack = back) }
            composable(Routes.ALARM_SPIKE) { AlarmSpikeScreen(onBack = back) }
            composable(Routes.DATABASE) { DatabaseDebugScreen(onBack = back) }
            composable(Routes.TIME_MACHINE) { TimeMachineScreen(onBack = back) }
            composable(Routes.ENGINE) { ReminderEngineDebugScreen(onBack = back) }
        }
    }
}

/** The end of the running pause, or null. Re-read every few seconds so the banner leaves on time. */
@Composable
private fun activePause(): Instant? {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val state by container.calmStore.state.collectAsStateWithLifecycle(null)
    val now by produceState(container.realClock.now()) {
        while (true) {
            delay(PAUSE_TICK_MS)
            value = container.realClock.now()
        }
    }
    return state?.pause?.until?.takeIf { it.isAfter(now) }
}

private const val PAUSE_TICK_MS = 5_000L

@Composable
private fun PauseSheetHost(onDismiss: () -> Unit) {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(null)
    val s = settings ?: return
    PauseSheet(tonight = s.snoozeTonight, holdsByDefault = !s.pauseExemptsEventsAndAlarms, onDismiss = onDismiss)
}

/** Titles of the RED Reminder Health rows, for the Home banner. Re-checked on every resume (MainActivity). */
@Composable
private fun healthProblems(): List<String> {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val status by container.setupRepository.status.collectAsStateWithLifecycle(null)
    return status?.let { HealthSummary.of(it).redChecks.map { check -> stringResource(check.rowText().title) } }.orEmpty()
}

private fun debugTools(navController: NavController): List<DebugTool> = listOf(
    DebugTool(R.string.spike_home_button) { navController.navigate(Routes.POPUP_SPIKE) },
    DebugTool(R.string.alarm_spike_home_button) { navController.navigate(Routes.ALARM_SPIKE) },
    DebugTool(R.string.db_debug_home_button) { navController.navigate(Routes.DATABASE) },
    DebugTool(R.string.time_machine_home_button) { navController.navigate(Routes.TIME_MACHINE) },
    DebugTool(R.string.engine_home_button) { navController.navigate(Routes.ENGINE) },
)
