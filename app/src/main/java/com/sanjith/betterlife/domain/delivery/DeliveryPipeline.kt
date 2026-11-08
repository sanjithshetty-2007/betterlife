package com.sanjith.betterlife.domain.delivery

import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReasonCode
import java.time.Duration
import java.time.Instant

/** Whether the item behind a reminder still wants it. */
public enum class ItemStatus { ACTIVE, COMPLETED, DELETED }

/** Everything the pipeline needs to know at the moment a reminder fires. */
public data class DeliveryInput(
    val importance: Importance,
    val itemStatus: ItemStatus,
    val device: PresentationInputs,
    val callActive: Boolean,
    val now: Instant,
    /** A calendar event or birthday (Pause leaves these alone unless the owner chose otherwise). */
    val calendarItem: Boolean = false,
    /** Pause, Quiet Hours and fatigue state (Prompt 12). */
    val calm: CalmInputs = CalmInputs.NONE,
    /** This firing is a snoozed reminder coming back: the owner still has to answer it, so fatigue never downgrades it. */
    val snoozedRepeat: Boolean = false,
)

public enum class DeliveryAction {
    /** Do not show anything (item gone or done). */
    DROP,

    /** Show it now, by [DeliveryDecision.path]. */
    DELIVER,

    /** Keep it and deliver it in the digest at [DeliveryDecision.holdUntil] (Pause, Quiet Hours). */
    HOLD,
}

/**
 * The outcome of one firing. [level] may be lower than the reminder's own importance (degraded);
 * [reasonCode] says why, for the Reminder Center. [path] is how to show it when delivered; NONE means
 * nothing can be shown at all.
 */
public data class DeliveryDecision(
    val action: DeliveryAction,
    val level: Importance,
    val path: PresentationPath,
    val reasonCode: ReasonCode?,
    val holdUntil: Instant? = null,
    /** Joins the pop-up of a reminder shown within the grouping window, so it is not a new interruption. */
    val grouped: Boolean = false,
)

/**
 * The single, ordered pipeline every firing runs through (context.md section 5.1):
 * 1 item still valid, 2 capability check, 3 phone call, 4 pause, 5 quiet hours, 6 fatigue, 7 deliver.
 * Pure and deterministic, so every rule and the order itself are unit tested.
 */
public object DeliveryPipeline {

    /** A step either finishes the pipeline with a decision or passes a (possibly changed) one on. */
    private sealed interface StepResult {
        data class Stop(val decision: DeliveryDecision) : StepResult
        data class Continue(val decision: DeliveryDecision) : StepResult
    }

    private class Step(val name: String, val run: (DeliveryInput, DeliveryDecision) -> StepResult)

    private val steps: List<Step> = listOf(
        Step("ITEM_VALID", ::itemValid),
        Step("CAPABILITY", ::capability),
        Step("IN_CALL", ::inCall),
        Step("PAUSE", ::pause),
        Step("QUIET_HOURS", ::quietHours),
        Step("FATIGUE", ::fatigue),
        Step("DELIVER", ::deliver),
    )

    /** The step names in the order they run. Tests pin this order. */
    public val STEP_ORDER: List<String> = steps.map { it.name }

    /**
     * Example: a FULL_SCREEN reminder for an active task while the owner uses the phone, overlay allowed,
     * no call -> `DeliveryDecision(DELIVER, FULL_SCREEN, OVERLAY, reasonCode = null)`.
     */
    public fun decide(input: DeliveryInput): DeliveryDecision {
        var decision = DeliveryDecision(DeliveryAction.DELIVER, input.importance, PresentationPath.NONE, reasonCode = null)
        for (step in steps) {
            when (val result = step.run(input, decision)) {
                is StepResult.Stop -> return result.decision
                is StepResult.Continue -> decision = result.decision
            }
        }
        return decision
    }

    // 1: a completed or deleted item drops its reminder.
    private fun itemValid(input: DeliveryInput, d: DeliveryDecision): StepResult = when (input.itemStatus) {
        ItemStatus.ACTIVE -> StepResult.Continue(d)
        ItemStatus.COMPLETED -> StepResult.Stop(d.copy(action = DeliveryAction.DROP, reasonCode = ReasonCode.ITEM_COMPLETED))
        ItemStatus.DELETED -> StepResult.Stop(d.copy(action = DeliveryAction.DROP, reasonCode = ReasonCode.ITEM_DELETED))
    }

    // 2: pick how to show it; a pop-up level that cannot pop up becomes a normal notification.
    private fun capability(input: DeliveryInput, d: DeliveryDecision): StepResult {
        if (d.level == Importance.NORMAL) {
            val path = if (input.device.notificationsEnabled) PresentationPath.NOTIFICATION_ONLY else PresentationPath.NONE
            val reason = if (path == PresentationPath.NONE) ReasonCode.PERMISSION_MISSING else d.reasonCode
            return StepResult.Continue(d.copy(path = path, reasonCode = reason))
        }
        val presentation = decidePresentation(input.device)
        return StepResult.Continue(
            when (presentation.path) {
                PresentationPath.OVERLAY, PresentationPath.FULL_SCREEN_INTENT -> d.copy(path = presentation.path)
                PresentationPath.NOTIFICATION_ONLY, PresentationPath.NONE ->
                    d.copy(level = Importance.NORMAL, path = presentation.path, reasonCode = ReasonCode.PERMISSION_MISSING)
            },
        )
    }

    // 3: never pop up over a phone call; a normal notification is enough.
    private fun inCall(input: DeliveryInput, d: DeliveryDecision): StepResult {
        val wouldPopUp = d.path == PresentationPath.OVERLAY || d.path == PresentationPath.FULL_SCREEN_INTENT
        if (!input.callActive || !wouldPopUp) return StepResult.Continue(d)
        val path = if (input.device.notificationsEnabled) PresentationPath.NOTIFICATION_ONLY else PresentationPath.NONE
        return StepResult.Continue(d.copy(level = Importance.NORMAL, path = path, reasonCode = ReasonCode.IN_CALL))
    }

    // 4: "I am busy". Holds NORMAL and FULL_SCREEN reminders of tasks (and habits, Prompt 14); calendar
    // items and the ALARM level only when the owner chose to hold them too.
    private fun pause(input: DeliveryInput, d: DeliveryDecision): StepResult {
        val pause = input.calm.pause?.takeIf { PauseRules.isActive(it, input.now) } ?: return StepResult.Continue(d)
        val exempt = (input.calendarItem && !pause.holdsCalendar) || (input.importance == Importance.ALARM && !pause.holdsAlarm)
        if (exempt) return StepResult.Continue(d)
        return StepResult.Stop(d.copy(action = DeliveryAction.HOLD, reasonCode = ReasonCode.PAUSED, holdUntil = pause.until))
    }

    // 5: Quiet Hours hold everything until the window ends; ALARM level passes when the setting allows.
    private fun quietHours(input: DeliveryInput, d: DeliveryDecision): StepResult {
        val until = input.calm.quietUntil?.takeIf { input.now.isBefore(it) } ?: return StepResult.Continue(d)
        if (input.importance == Importance.ALARM && input.calm.alarmBypassesQuiet) return StepResult.Continue(d)
        return StepResult.Stop(d.copy(action = DeliveryAction.HOLD, reasonCode = ReasonCode.QUIET_HOURS, holdUntil = until))
    }

    // 6: fatigue protection, for pop-ups only. In order: the calm period after repeated ignores, joining
    // a pop-up shown within the grouping window, then the hourly maximum of new pop-ups.
    private fun fatigue(input: DeliveryInput, d: DeliveryDecision): StepResult {
        val f = input.calm.fatigue ?: return StepResult.Continue(d)
        val popsUp = d.path == PresentationPath.OVERLAY || d.path == PresentationPath.FULL_SCREEN_INTENT
        if (!popsUp || input.snoozedRepeat || (d.level == Importance.ALARM && f.alarmExempt)) return StepResult.Continue(d)
        val calm = f.downgradeEnabled && f.calmUntil?.let { input.now.isBefore(it) } == true
        if (calm) return StepResult.Continue(downgrade(input, d))
        val lastGroupStart = f.recentPopups.filter { !it.isAfter(input.now) }.maxOrNull()
        if (lastGroupStart != null && !Duration.between(lastGroupStart, input.now).minus(f.groupWindow).isPositive) {
            return StepResult.Continue(d.copy(grouped = true, reasonCode = ReasonCode.FATIGUE_GROUPED))
        }
        val windowStart = input.now.minus(FATIGUE_WINDOW)
        val inLastHour = f.recentPopups.count { it.isAfter(windowStart) && !it.isAfter(input.now) }
        return StepResult.Continue(if (inLastHour >= f.maxPerHour) downgrade(input, d) else d)
    }

    private fun downgrade(input: DeliveryInput, d: DeliveryDecision): DeliveryDecision {
        val path = if (input.device.notificationsEnabled) PresentationPath.NOTIFICATION_ONLY else PresentationPath.NONE
        return d.copy(level = Importance.NORMAL, path = path, reasonCode = ReasonCode.FATIGUE_DOWNGRADED)
    }

    // 7: deliver whatever survived.
    @Suppress("UNUSED_PARAMETER")
    private fun deliver(input: DeliveryInput, d: DeliveryDecision): StepResult =
        StepResult.Stop(d.copy(action = DeliveryAction.DELIVER))
}
