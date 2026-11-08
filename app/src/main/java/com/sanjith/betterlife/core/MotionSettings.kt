package com.sanjith.betterlife.core

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val DEFAULT_ANIMATOR_SCALE = 1f

/** True when the owner turned animations off (Developer options > Animator duration scale = off). */
public fun Context.isReducedMotion(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, DEFAULT_ANIMATOR_SCALE) == 0f

/** The same, read once per place on screen: a settings read on every recomposition of a list adds up. */
@Composable
public fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.isReducedMotion() }
}
