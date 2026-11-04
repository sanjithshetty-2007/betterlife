package com.sanjith.betterlife

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sanjith.betterlife.domain.model.EventType
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
            HomeScreen(
                onOpenTasks = { navController.navigate(Routes.TASKS) },
                onOpenSetup = { navController.navigate(Routes.SETUP) },
                onOpenAgenda = { navController.navigate(Routes.AGENDA) },
                // Debug tools are unreachable in release builds.
                debugTools = if (BuildConfig.DEBUG) debugTools(navController) else emptyList(),
            )
        }
        composable(Routes.SETUP) { SetupScreen(onBack = back) }
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

private fun debugTools(navController: NavController): List<DebugTool> = listOf(
    DebugTool(R.string.spike_home_button) { navController.navigate(Routes.POPUP_SPIKE) },
    DebugTool(R.string.alarm_spike_home_button) { navController.navigate(Routes.ALARM_SPIKE) },
    DebugTool(R.string.db_debug_home_button) { navController.navigate(Routes.DATABASE) },
    DebugTool(R.string.time_machine_home_button) { navController.navigate(Routes.TIME_MACHINE) },
    DebugTool(R.string.engine_home_button) { navController.navigate(Routes.ENGINE) },
)
