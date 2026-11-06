package com.sanjith.betterlife.reminders.engine

import com.sanjith.betterlife.domain.delivery.ItemStatus
import com.sanjith.betterlife.domain.delivery.Pause
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
public enum class RescheduleTrigger { BOOT, APP_UPDATED, TIME_CHANGED, TIMEZONE_CHANGED, APP_LAUNCH, DAILY, AFTER_FIRE, MANUAL, SYNC_PULL }

/** What the engine asks about habits (Prompt 15); the app answers with the habit day and its logs. */
public interface HabitReminderHooks {
    /** True when the habit needs no reminder at [at]: its habit day is already done, kept or paused. */
    public suspend fun settled(habitId: String, at: java.time.Instant): Boolean

    /** The pop-up's Done on a habit reminder: tick today's habit day. */
    public suspend fun tickFromReminder(habitId: String)
}

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

    /**
     * When the pop-ups that started a group were shown since [since] (from the reminder log), for
     * fatigue protection: grouped pop-ups are not counted.
     */
    public suspend fun recentPopupStarts(since: Instant): List<Instant> = emptyList()

    /** Reminders held by Pause or Quiet Hours and not yet delivered in a digest. */
    public suspend fun heldReminderIds(): List<String> = emptyList()

    /** Every reminder that has an alarm set right now (cancelled before a restore replaces the data). */
    public suspend fun scheduledIds(): List<String> = emptyList()
}

/**
 * Pause, the pending digest time and the ignore streak (Prompt 12). Kept on disk, so a restart or a
 * killed process never loses a pause or resets the fatigue counters.
 */
public data class CalmState(
    val pause: Pause? = null,
    val digestAt: Instant? = null,
    val ignoreStreak: Int = 0,
    val calmUntil: Instant? = null,
)

public interface CalmStateStore {
    public suspend fun read(): CalmState
    public suspend fun update(change: (CalmState) -> CalmState)
}

/** For tests and previews only; the app uses the DataStore-backed store. */
public class InMemoryCalmStateStore(private var state: CalmState = CalmState()) : CalmStateStore {
    override suspend fun read(): CalmState = state
    override suspend fun update(change: (CalmState) -> CalmState) {
        state = change(state)
    }
}

public interface AlarmScheduler {
    public fun canScheduleExact(): Boolean

    /** Setting the same reminder again replaces its alarm; it never adds a second one. */
    public fun set(reminderId: String, fireAt: Instant, kind: AlarmKind)
    public fun cancel(reminderId: String)

    /** The one digest alarm: delivers held reminders when Pause or Quiet Hours end. Setting again replaces it. */
    public fun setDigest(at: Instant) {}
    public fun cancelDigest() {}
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

    /** ONE notification for the reminders held by Pause or Quiet Hours (context.md 5.1). */
    public fun postDigest(titles: List<String>) {}

    /** Tells the owner once that pop-ups stay calm until [until] after being ignored several times. */
    public fun postCalmNotice(until: Instant) {}
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
