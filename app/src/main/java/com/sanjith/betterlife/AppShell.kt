package com.sanjith.betterlife

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.sanjith.betterlife.ui.nav.FloatingTabBar
import com.sanjith.betterlife.ui.nav.TabItem
import com.sanjith.betterlife.ui.theme.Motion

/** A main screen: below the status bar, down to the bottom edge. Its last rows clear the bar with tabContentPadding. */
internal fun NavGraphBuilder.tab(route: String, content: @Composable () -> Unit) {
    composable(route) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).consumeWindowInsets(WindowInsets.navigationBars)) { content() }
    }
}

/** Any other screen: inside the system bars. */
internal fun NavGraphBuilder.screen(route: String, content: @Composable () -> Unit) {
    composable(route) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) { content() }
    }
}

/** The five main screens; the bottom bar shows only on these (Prompt 17). */
private val TABS = listOf(
    TabItem(Routes.HOME, R.drawable.ic_sun, R.string.nav_today),
    TabItem(Routes.TASKS, R.drawable.ic_check_circle, R.string.nav_tasks),
    TabItem(Routes.HABITS, R.drawable.ic_grid, R.string.nav_habits),
    TabItem(Routes.AGENDA, R.drawable.ic_calendar, R.string.nav_calendar),
    TabItem(Routes.MORE, R.drawable.ic_apps, R.string.nav_more),
)

internal val TAB_ROUTES: List<String> = TABS.map { it.route }

/** Switches tabs without piling screens up: one copy of each, back goes to Today. */
internal fun NavController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Lives apart from the NavHost so that moving between screens recomposes only the bar. */
@Composable
internal fun BoxScope.TabBar(navController: NavController) {
    val current by navController.currentBackStackEntryAsState()
    val route = current?.destination?.route
    // While the bar leaves for a pushed screen it keeps showing the tab it left from.
    var lastTab by remember { mutableStateOf(Routes.HOME) }
    SideEffect { if (route != null && route in TAB_ROUTES) lastTab = route }
    AnimatedVisibility(
        visible = route == null || route in TAB_ROUTES,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = fadeIn(tween(Motion.BAR_MS)) + slideInVertically(tween(Motion.BAR_MS)) { it / 2 },
        exit = fadeOut(tween(Motion.BAR_MS)) + slideOutVertically(tween(Motion.BAR_MS)) { it / 2 },
    ) {
        FloatingTabBar(TABS, route?.takeIf { it in TAB_ROUTES } ?: lastTab, onSelect = { navController.navigateTab(it) })
    }
}
