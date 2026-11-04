package com.sanjith.betterlife

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sanjith.betterlife.feature.debug.AlarmSpikeScreen
import com.sanjith.betterlife.feature.debug.DatabaseDebugScreen
import com.sanjith.betterlife.feature.debug.PopupSpikeScreen
import com.sanjith.betterlife.feature.debug.ReminderEngineDebugScreen
import com.sanjith.betterlife.feature.debug.TimeMachineScreen
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.feature.home.DebugTool
import com.sanjith.betterlife.feature.home.HomeScreen
import com.sanjith.betterlife.feature.item.ItemPlaceholderScreen
import com.sanjith.betterlife.feature.setup.SetupScreen

/** Screen routes. Plain strings until a screen needs arguments. */
internal object Routes {
    const val HOME = "home"
    const val SETUP = "setup"
    const val ITEM = "item"
    const val POPUP_SPIKE = "debug/popup-spike"
    const val ALARM_SPIKE = "debug/alarm-spike"
    const val DATABASE = "debug/database"
    const val TIME_MACHINE = "debug/time-machine"
    const val ENGINE = "debug/reminder-engine"
}

/** All screens of the single Activity. NavHost also handles the system (predictive) back gesture. */
@Composable
internal fun AppNavHost(itemToOpen: ItemRef?, onItemOpened: () -> Unit) {
    val navController = rememberNavController()
    // A pop-up's Reschedule button asks to open an item's editor.
    LaunchedEffect(itemToOpen) {
        itemToOpen ?: return@LaunchedEffect
        navController.navigate("${Routes.ITEM}/${itemToOpen.type.name}/${itemToOpen.id}")
        onItemOpened()
    }
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSetup = { navController.navigate(Routes.SETUP) },
                // Debug tools are unreachable in release builds.
                debugTools = if (BuildConfig.DEBUG) debugTools(navController) else emptyList(),
            )
        }
        composable(Routes.SETUP) {
            SetupScreen(onBack = { navController.popBackStack() })
        }
        composable("${Routes.ITEM}/{type}/{id}") { entry ->
            val type = entry.arguments?.getString("type")?.let { name -> ReminderItemType.entries.firstOrNull { it.name == name } }
            val id = entry.arguments?.getString("id")
            if (type != null && id != null) {
                ItemPlaceholderScreen(type, id, onBack = { navController.popBackStack() })
            }
        }
        if (BuildConfig.DEBUG) {
            composable(Routes.POPUP_SPIKE) {
                PopupSpikeScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ALARM_SPIKE) {
                AlarmSpikeScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.DATABASE) {
                DatabaseDebugScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.TIME_MACHINE) {
                TimeMachineScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ENGINE) {
                ReminderEngineDebugScreen(onBack = { navController.popBackStack() })
            }
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
