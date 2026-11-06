package com.sanjith.betterlife.domain.setup

/** Traffic-light status of one setup check. Ordered from best to worst so the worst can be found with maxOf. */
public enum class SetupLevel { GREEN, YELLOW, RED }

/** Every permission or phone setting the reminders depend on. */
public enum class SetupCheck {
    NOTIFICATIONS,
    EXACT_ALARMS,
    /** The reminder notification channels have not been turned off or lowered in system settings. */
    CHANNELS,
    DRAW_OVER_APPS,
    FULL_SCREEN_INTENT,
    BATTERY_UNRESTRICTED,
    VIVO_AUTOSTART,
    VIVO_BACKGROUND_POPUPS,
    VIVO_LOCK_SCREEN,
    VIVO_RECENTS_LOCK,
    ;

    /** True for checks the app cannot read and the owner confirms by hand. */
    public val isManual: Boolean
        get() = this in MANUAL_CHECKS

    public companion object {
        public val MANUAL_CHECKS: Set<SetupCheck> =
            setOf(VIVO_AUTOSTART, VIVO_BACKGROUND_POPUPS, VIVO_LOCK_SCREEN, VIVO_RECENTS_LOCK)
    }
}

/**
 * Why a check has its level. The UI turns each reason into a plain-language sentence,
 * so domain code never holds user-visible text.
 */
public enum class SetupReason {
    GRANTED,
    NOTIFICATIONS_OFF,
    EXACT_ALARMS_DENIED,
    CHANNEL_BLOCKED,
    CHANNEL_LOWERED,
    OVERLAY_DENIED,
    FULL_SCREEN_DENIED,
    BATTERY_RESTRICTED,
    CONFIRMED_BY_OWNER,
    NOT_CONFIRMED,
}

/** What the owner did to the reminder notification channels in system settings. */
public enum class ChannelState { OK, LOWERED, BLOCKED }

/** Raw results read from Android APIs. */
public data class DeviceChecks(
    val notificationsEnabled: Boolean,
    val exactAlarmsAllowed: Boolean,
    val canDrawOverlays: Boolean,
    val canUseFullScreenIntent: Boolean,
    val batteryUnrestricted: Boolean,
    val channels: ChannelState = ChannelState.OK,
)

/** One row of Reminder Health (the Setup Checklist before Prompt 11). */
public data class SetupItem(
    val check: SetupCheck,
    val level: SetupLevel,
    val reason: SetupReason,
)

/** Every Reminder Health row plus the worst level, which drives the home banner. */
public data class SetupStatus(
    val items: List<SetupItem>,
) {
    val overall: SetupLevel
        get() = items.maxOfOrNull { it.level } ?: SetupLevel.GREEN
}
