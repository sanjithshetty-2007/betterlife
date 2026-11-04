package com.sanjith.betterlife

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sanjith.betterlife.feature.home.HomeScreen
import com.sanjith.betterlife.feature.setup.SetupScreen

/** Screen routes. Plain strings until a screen needs arguments. */
internal object Routes {
    const val HOME = "home"
    const val SETUP = "setup"
}

/** All screens of the single Activity. NavHost also handles the system (predictive) back gesture. */
@Composable
internal fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onOpenSetup = { navController.navigate(Routes.SETUP) })
        }
        composable(Routes.SETUP) {
            SetupScreen(onBack = { navController.popBackStack() })
        }
    }
}
