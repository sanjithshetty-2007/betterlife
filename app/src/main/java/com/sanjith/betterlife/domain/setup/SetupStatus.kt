package com.sanjith.betterlife.domain.setup

/** Traffic-light status of one setup check. Ordered from best to worst so the worst can be found with maxOf. */
public enum class SetupLevel { GREEN, YELLOW, RED }

/** Every permission or phone setting the reminders depend on. */
public enum class SetupCheck {
    NOTIFICATIONS,
    EXACT_ALARMS,
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
    OVERLAY_DENIED,
    FULL_SCREEN_DENIED,
    BATTERY_RESTRICTED,
    CONFIRMED_BY_OWNER,
    NOT_CONFIRMED,
}

/** Raw results read from Android APIs. */
public data class DeviceChecks(
    val notificationsEnabled: Boolean,
    val exactAlarmsAllowed: Boolean,
    val canDrawOverlays: Boolean,
    val canUseFullScreenIntent: Boolean,
    val batteryUnrestricted: Boolean,
)

/** One row of the Setup Checklist. */
public data class SetupItem(
    val check: SetupCheck,
    val level: SetupLevel,
    val reason: SetupReason,
)

/** The whole checklist plus its worst level, used later for the Reminder Health banner. */
public data class SetupStatus(
    val items: List<SetupItem>,
) {
    val overall: SetupLevel
        get() = items.maxOfOrNull { it.level } ?: SetupLevel.GREEN
}
