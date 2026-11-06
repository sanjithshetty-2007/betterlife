package com.sanjith.betterlife.feature.center

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.model.ReasonCode
import com.sanjith.betterlife.domain.model.ReminderLogStatus
import com.sanjith.betterlife.domain.reminder.CenterFilter

/** The plain sentence for a reason code (context.md 5.1). Every code has one. */
@StringRes
public fun ReasonCode.sentence(): Int = when (this) {
    ReasonCode.QUIET_HOURS -> R.string.reason_quiet_hours
    ReasonCode.PAUSED -> R.string.reason_paused
    ReasonCode.FATIGUE_GROUPED -> R.string.reason_fatigue_grouped
    ReasonCode.FATIGUE_DOWNGRADED -> R.string.reason_fatigue_downgraded
    ReasonCode.IN_CALL -> R.string.reason_in_call
    ReasonCode.PERMISSION_MISSING -> R.string.reason_permission_missing
    ReasonCode.ITEM_COMPLETED -> R.string.reason_item_completed
    ReasonCode.ITEM_DELETED -> R.string.reason_item_deleted
    ReasonCode.DEVICE_OFF -> R.string.reason_device_off
    ReasonCode.EXACT_ALARM_DENIED -> R.string.reason_exact_alarm_denied
}

/**
 * The reason line under a Reminder Center row: the code's sentence when there is one; for Held,
 * Missed and Failed rows without a code a general sentence; otherwise nothing.
 */
@StringRes
public fun reasonLine(status: ReminderLogStatus, code: ReasonCode?): Int? = code?.sentence() ?: when (status) {
    ReminderLogStatus.MISSED -> R.string.reason_missed_no_answer
    ReminderLogStatus.FAILED -> R.string.reason_failed_unknown
    ReminderLogStatus.HELD -> R.string.reason_held_unknown
    else -> null
}

/** How a status looks: its word, icon and colour role. */
public data class StatusLook(@StringRes val label: Int, @DrawableRes val icon: Int, val tone: Tone)

public enum class Tone { GOOD, WARN, BAD, NEUTRAL }

public fun ReminderLogStatus.look(): StatusLook = when (this) {
    ReminderLogStatus.SCHEDULED -> StatusLook(R.string.center_status_scheduled, R.drawable.ic_schedule, Tone.NEUTRAL)
    ReminderLogStatus.FIRED -> StatusLook(R.string.center_status_fired, R.drawable.ic_alarm, Tone.GOOD)
    ReminderLogStatus.COMPLETED -> StatusLook(R.string.center_status_completed, R.drawable.ic_check_circle, Tone.GOOD)
    ReminderLogStatus.SNOOZED -> StatusLook(R.string.center_status_snoozed, R.drawable.ic_snooze, Tone.WARN)
    ReminderLogStatus.RESCHEDULED -> StatusLook(R.string.center_status_rescheduled, R.drawable.ic_repeat, Tone.WARN)
    ReminderLogStatus.DISMISSED -> StatusLook(R.string.center_status_dismissed, R.drawable.ic_info, Tone.NEUTRAL)
    ReminderLogStatus.MISSED -> StatusLook(R.string.center_status_missed, R.drawable.ic_error, Tone.BAD)
    ReminderLogStatus.HELD -> StatusLook(R.string.center_status_held, R.drawable.ic_pause_circle, Tone.NEUTRAL)
    ReminderLogStatus.FAILED -> StatusLook(R.string.center_status_failed, R.drawable.ic_error, Tone.BAD)
}

@StringRes
public fun CenterFilter.label(): Int = status?.look()?.label ?: R.string.center_filter_all
