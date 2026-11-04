package com.sanjith.betterlife.domain.delivery

import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReasonCode
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
)

public enum class DeliveryAction {
    /** Do not show anything (item gone or done). */
    DROP,

    /** Show it now, by [DeliveryDecision.path]. */
    DELIVER,

    /** Keep it and deliver at [DeliveryDecision.holdUntil] (Pause, Quiet Hours: Prompt 12). */
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

    // 4-6: real rules arrive in Prompt 12 (reason codes PAUSED, QUIET_HOURS, FATIGUE_GROUPED,
    // FATIGUE_DOWNGRADED). Until then they pass every reminder through unchanged.
    @Suppress("UNUSED_PARAMETER")
    private fun pause(input: DeliveryInput, d: DeliveryDecision): StepResult = StepResult.Continue(d)

    @Suppress("UNUSED_PARAMETER")
    private fun quietHours(input: DeliveryInput, d: DeliveryDecision): StepResult = StepResult.Continue(d)

    @Suppress("UNUSED_PARAMETER")
    private fun fatigue(input: DeliveryInput, d: DeliveryDecision): StepResult = StepResult.Continue(d)

    // 7: deliver whatever survived.
    @Suppress("UNUSED_PARAMETER")
    private fun deliver(input: DeliveryInput, d: DeliveryDecision): StepResult =
        StepResult.Stop(d.copy(action = DeliveryAction.DELIVER))
}
