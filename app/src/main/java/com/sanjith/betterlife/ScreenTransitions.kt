package com.sanjith.betterlife

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.sanjith.betterlife.ui.theme.Motion

private const val ENTER_SHIFT = 10
private const val EXIT_SHIFT = 20

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in TAB_ROUTES && targetState.destination.route in TAB_ROUTES

/**
 * Tabs swap with a very quick fade; a screen opened from another slides a little and fades, and comes
 * back the other way. Navigation's own default is a 700 ms fade, which is what made the app feel slow.
 */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.screenEnter(reduced: Boolean, forward: Boolean): EnterTransition = when {
    reduced -> EnterTransition.None
    isTabSwitch() -> fadeIn(tween(Motion.TAB_SWITCH_MS))
    else -> fadeIn(tween(Motion.PUSH_MS)) +
        slideInHorizontally(tween(Motion.PUSH_MS, easing = LinearOutSlowInEasing)) { width -> (if (forward) width else -width) / ENTER_SHIFT }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.screenExit(reduced: Boolean, forward: Boolean): ExitTransition = when {
    reduced -> ExitTransition.None
    isTabSwitch() -> fadeOut(tween(Motion.TAB_SWITCH_MS))
    else -> fadeOut(tween(Motion.PUSH_MS / 2)) +
        slideOutHorizontally(tween(Motion.PUSH_MS, easing = FastOutLinearInEasing)) { width -> (if (forward) -width else width) / EXIT_SHIFT }
}
