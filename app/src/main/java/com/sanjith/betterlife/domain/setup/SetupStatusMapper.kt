package com.sanjith.betterlife.domain.setup

/**
 * Turns raw permission results and the owner's manual ticks into the Setup Checklist.
 *
 * Missing permissions that stop a reminder from showing are RED. A restricted battery is YELLOW:
 * reminders usually still fire but may be late. Manual Vivo checks are YELLOW until ticked, because
 * the app cannot read them. Vivo rows are left out on other phone brands.
 *
 * Example: `buildSetupStatus(allGranted, manualTicks = emptySet(), isVivo = false)` gives six GREEN rows.
 */
public fun buildSetupStatus(
    device: DeviceChecks,
    manualTicks: Set<SetupCheck>,
    isVivo: Boolean,
): SetupStatus {
    val detected = listOf(
        required(SetupCheck.NOTIFICATIONS, device.notificationsEnabled, SetupReason.NOTIFICATIONS_OFF),
        required(SetupCheck.EXACT_ALARMS, device.exactAlarmsAllowed, SetupReason.EXACT_ALARMS_DENIED),
        channelItem(device.channels),
        required(SetupCheck.DRAW_OVER_APPS, device.canDrawOverlays, SetupReason.OVERLAY_DENIED),
        required(SetupCheck.FULL_SCREEN_INTENT, device.canUseFullScreenIntent, SetupReason.FULL_SCREEN_DENIED),
        recommended(SetupCheck.BATTERY_UNRESTRICTED, device.batteryUnrestricted, SetupReason.BATTERY_RESTRICTED),
    )
    val manual = if (isVivo) {
        SetupCheck.MANUAL_CHECKS.sortedBy { it.ordinal }.map { manualItem(it, it in manualTicks) }
    } else {
        emptyList()
    }
    return SetupStatus(detected + manual)
}

private fun required(check: SetupCheck, granted: Boolean, deniedReason: SetupReason): SetupItem =
    if (granted) {
        SetupItem(check, SetupLevel.GREEN, SetupReason.GRANTED)
    } else {
        SetupItem(check, SetupLevel.RED, deniedReason)
    }

private fun recommended(check: SetupCheck, granted: Boolean, deniedReason: SetupReason): SetupItem =
    if (granted) {
        SetupItem(check, SetupLevel.GREEN, SetupReason.GRANTED)
    } else {
        SetupItem(check, SetupLevel.YELLOW, deniedReason)
    }

// A turned-off reminder channel hides reminders (RED); a lowered one makes them quieter (YELLOW).
private fun channelItem(state: ChannelState): SetupItem = when (state) {
    ChannelState.OK -> SetupItem(SetupCheck.CHANNELS, SetupLevel.GREEN, SetupReason.GRANTED)
    ChannelState.LOWERED -> SetupItem(SetupCheck.CHANNELS, SetupLevel.YELLOW, SetupReason.CHANNEL_LOWERED)
    ChannelState.BLOCKED -> SetupItem(SetupCheck.CHANNELS, SetupLevel.RED, SetupReason.CHANNEL_BLOCKED)
}

private fun manualItem(check: SetupCheck, ticked: Boolean): SetupItem =
    if (ticked) {
        SetupItem(check, SetupLevel.GREEN, SetupReason.CONFIRMED_BY_OWNER)
    } else {
        SetupItem(check, SetupLevel.YELLOW, SetupReason.NOT_CONFIRMED)
    }
