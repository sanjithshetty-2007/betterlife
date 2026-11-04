package com.sanjith.betterlife.core

import android.content.Context
import android.provider.Settings

private const val DEFAULT_ANIMATOR_SCALE = 1f

/** True when the owner turned animations off (Developer options > Animator duration scale = off). */
public fun Context.isReducedMotion(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, DEFAULT_ANIMATOR_SCALE) == 0f
