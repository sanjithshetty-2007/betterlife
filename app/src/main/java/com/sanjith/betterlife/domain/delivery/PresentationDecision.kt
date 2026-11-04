package com.sanjith.betterlife.domain.delivery

/** How a reminder is put in front of the owner. */
public enum class PresentationPath {
    /** Our own window drawn over the app in use (screen on and unlocked). */
    OVERLAY,

    /** A notification whose full-screen intent opens the pop-up Activity (locked or screen off). */
    FULL_SCREEN_INTENT,

    /** A plain notification, because the pop-up paths are not allowed. */
    NOTIFICATION_ONLY,

    /** Nothing can be shown: no overlay possible and notifications are off. */
    NONE,
}

/** Why that path was chosen. Written to the debug log and later to the reminder log. */
public enum class PresentationReason {
    PHONE_IN_USE,
    LOCKED_OR_SCREEN_OFF,
    OVERLAY_DENIED,
    FULL_SCREEN_DENIED,
    NOTIFICATIONS_OFF,
}

/** The phone state at the moment a reminder fires. */
public data class PresentationInputs(
    val isInteractive: Boolean,
    val isKeyguardLocked: Boolean,
    val canDrawOverlays: Boolean,
    val canUseFullScreenIntent: Boolean,
    val notificationsEnabled: Boolean,
)

public data class PresentationDecision(
    val path: PresentationPath,
    val reason: PresentationReason,
)

/**
 * Picks the pop-up path. A full-screen intent only opens full screen when the phone is locked or the
 * screen is off; while the owner uses the phone only our own overlay window can cover the app in use.
 * Overlay windows never show above the lock screen, so a locked phone always goes through notifications.
 *
 * Example: screen on, unlocked, overlay allowed -> `PresentationDecision(OVERLAY, PHONE_IN_USE)`.
 */
public fun decidePresentation(inputs: PresentationInputs): PresentationDecision {
    val phoneInUse = inputs.isInteractive && !inputs.isKeyguardLocked
    return when {
        phoneInUse && inputs.canDrawOverlays ->
            PresentationDecision(PresentationPath.OVERLAY, PresentationReason.PHONE_IN_USE)
        !inputs.notificationsEnabled ->
            PresentationDecision(PresentationPath.NONE, PresentationReason.NOTIFICATIONS_OFF)
        phoneInUse ->
            PresentationDecision(PresentationPath.NOTIFICATION_ONLY, PresentationReason.OVERLAY_DENIED)
        inputs.canUseFullScreenIntent ->
            PresentationDecision(PresentationPath.FULL_SCREEN_INTENT, PresentationReason.LOCKED_OR_SCREEN_OFF)
        else ->
            PresentationDecision(PresentationPath.NOTIFICATION_ONLY, PresentationReason.FULL_SCREEN_DENIED)
    }
}
