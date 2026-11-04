package com.sanjith.betterlife.reminders.engine

import com.sanjith.betterlife.domain.delivery.ItemStatus
import com.sanjith.betterlife.domain.delivery.PresentationInputs
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReasonCode
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.model.ReminderLogStatus
import com.sanjith.betterlife.domain.model.ReminderState
import com.sanjith.betterlife.domain.reminder.AlarmKind
import com.sanjith.betterlife.domain.reminder.ScheduleTarget
import java.time.Instant

// The reminder engine talks to Android and the database only through these interfaces, so its logic
// runs in fast PC tests with fakes (agent.md section 3).

/** What started a full reschedule. Only BOOT turns missed reminders into DEVICE_OFF entries. */
public enum class RescheduleTrigger { BOOT, APP_UPDATED, TIME_CHANGED, TIMEZONE_CHANGED, APP_LAUNCH, DAILY, AFTER_FIRE, MANUAL }

/**
 * What the engine needs to know about one reminder when it fires; the extra fields fill the pop-up
 * and its "Why am I seeing this?" panel.
 */
public data class FireContext(
    val reminderId: String,
    val importance: Importance,
    val itemStatus: ItemStatus,
    val title: String,
    val recurring: Boolean,
    val offsetMinutes: Int,
    val itemType: ReminderItemType? = null,
    val itemId: String? = null,
    val notes: String? = null,
    val itemTime: Instant? = null,
    val itemAllDay: Boolean = false,
    val eventEnd: Instant? = null,
    val snoozeCount: Int = 0,
)

public interface ReminderStore {
    public suspend fun loadTargets(): List<ScheduleTarget>
    public suspend fun idsToUnschedule(): List<String>
    public suspend fun setScheduledFireAt(reminderId: String, fireAt: Instant?)
    public suspend fun fireContext(reminderId: String): FireContext?

    /** Atomically records delivery of [fireAt]; false if it was already recorded (a duplicate alarm). */
    public suspend fun markFired(reminderId: String, fireAt: Instant, state: ReminderState): Boolean

    /** Sets the snooze time and returns the new snooze count. */
    public suspend fun snooze(reminderId: String, until: Instant): Int

    /** Ends the reminder's current round (clears any snooze) in [state]. */
    public suspend fun finish(reminderId: String, state: ReminderState)

    /** Marks the task or standalone item behind a reminder done (Done on the pop-up). Does nothing for events. */
    public suspend fun completeTask(reminderId: String)
}

public interface AlarmScheduler {
    public fun canScheduleExact(): Boolean

    /** Setting the same reminder again replaces its alarm; it never adds a second one. */
    public fun set(reminderId: String, fireAt: Instant, kind: AlarmKind)
    public fun cancel(reminderId: String)
}

public interface DeviceStateReader {
    public fun presentationInputs(): PresentationInputs
    public fun callActive(): Boolean
}

public interface ReminderPresenter {
    /** Full-screen pop-up (overlay or lock-screen activity, decided at show time). */
    public fun showPopup(context: FireContext, level: Importance)
    public fun postNotification(reminderId: String, title: String, level: Importance)

    /** ONE notification for reminders missed while the phone was off; shows a count, never titles. */
    public fun postMissedSummary(count: Int)
}

public fun interface ReminderLogSink {
    public suspend fun append(
        reminderId: String,
        status: ReminderLogStatus,
        level: Importance,
        reasonCode: ReasonCode?,
        detail: String?,
        firedAt: Instant,
    )
}
