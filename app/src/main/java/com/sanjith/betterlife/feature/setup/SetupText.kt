package com.sanjith.betterlife.feature.setup

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.setup.SetupCheck
import com.sanjith.betterlife.domain.setup.SetupLevel
import com.sanjith.betterlife.domain.setup.SetupReason

/** Display resources for one checklist row. [howTo] is null when the system page needs no directions. */
internal data class SetupRowText(
    @StringRes val title: Int,
    @StringRes val why: Int,
    @StringRes val howTo: Int?,
    @DrawableRes val icon: Int,
)

internal fun SetupCheck.rowText(): SetupRowText = when (this) {
    SetupCheck.NOTIFICATIONS -> SetupRowText(
        R.string.setup_notifications_title, R.string.setup_notifications_why, null, R.drawable.ic_notifications,
    )
    SetupCheck.EXACT_ALARMS -> SetupRowText(
        R.string.setup_exact_alarms_title, R.string.setup_exact_alarms_why, null, R.drawable.ic_alarm,
    )
    SetupCheck.DRAW_OVER_APPS -> SetupRowText(
        R.string.setup_overlay_title, R.string.setup_overlay_why, null, R.drawable.ic_layers,
    )
    SetupCheck.FULL_SCREEN_INTENT -> SetupRowText(
        R.string.setup_full_screen_title, R.string.setup_full_screen_why, null, R.drawable.ic_fullscreen,
    )
    SetupCheck.BATTERY_UNRESTRICTED -> SetupRowText(
        R.string.setup_battery_title, R.string.setup_battery_why, R.string.setup_battery_how, R.drawable.ic_battery,
    )
    SetupCheck.VIVO_AUTOSTART -> SetupRowText(
        R.string.setup_vivo_autostart_title, R.string.setup_vivo_autostart_why,
        R.string.setup_vivo_autostart_how, R.drawable.ic_phone,
    )
    SetupCheck.VIVO_BACKGROUND_POPUPS -> SetupRowText(
        R.string.setup_vivo_popups_title, R.string.setup_vivo_popups_why,
        R.string.setup_vivo_popups_how, R.drawable.ic_phone,
    )
    SetupCheck.VIVO_LOCK_SCREEN -> SetupRowText(
        R.string.setup_vivo_lock_screen_title, R.string.setup_vivo_lock_screen_why,
        R.string.setup_vivo_lock_screen_how, R.drawable.ic_phone,
    )
    SetupCheck.VIVO_RECENTS_LOCK -> SetupRowText(
        R.string.setup_vivo_recents_title, R.string.setup_vivo_recents_why,
        R.string.setup_vivo_recents_how, R.drawable.ic_phone,
    )
}

@StringRes
internal fun SetupReason.text(): Int = when (this) {
    SetupReason.GRANTED -> R.string.setup_reason_granted
    SetupReason.NOTIFICATIONS_OFF -> R.string.setup_reason_notifications_off
    SetupReason.EXACT_ALARMS_DENIED -> R.string.setup_reason_exact_alarms_denied
    SetupReason.OVERLAY_DENIED -> R.string.setup_reason_overlay_denied
    SetupReason.FULL_SCREEN_DENIED -> R.string.setup_reason_full_screen_denied
    SetupReason.BATTERY_RESTRICTED -> R.string.setup_reason_battery_restricted
    SetupReason.CONFIRMED_BY_OWNER -> R.string.setup_reason_confirmed
    SetupReason.NOT_CONFIRMED -> R.string.setup_reason_not_confirmed
}

@StringRes
internal fun SetupLevel.label(): Int = when (this) {
    SetupLevel.GREEN -> R.string.setup_level_green
    SetupLevel.YELLOW -> R.string.setup_level_yellow
    SetupLevel.RED -> R.string.setup_level_red
}
