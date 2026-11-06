package com.sanjith.betterlife.domain.delivery

import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReasonCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

public class DeliveryPipelineTest {

    private val inUse = PresentationInputs(
        isInteractive = true,
        isKeyguardLocked = false,
        canDrawOverlays = true,
        canUseFullScreenIntent = true,
        notificationsEnabled = true,
    )
    private val locked = inUse.copy(isInteractive = false, isKeyguardLocked = true)

    private fun input(
        importance: Importance = Importance.FULL_SCREEN,
        status: ItemStatus = ItemStatus.ACTIVE,
        device: PresentationInputs = inUse,
        call: Boolean = false,
    ) = DeliveryInput(importance, status, device, call, Instant.parse("2026-10-08T12:30:00Z"))

    @Test
    public fun stepsRunInTheOrderFromContextMd() {
        assertEquals(
            listOf("ITEM_VALID", "CAPABILITY", "IN_CALL", "PAUSE", "QUIET_HOURS", "FATIGUE", "DELIVER"),
            DeliveryPipeline.STEP_ORDER,
        )
    }

    @Test
    public fun fullScreenWhileInUseIsAnOverlay() {
        assertEquals(
            DeliveryDecision(DeliveryAction.DELIVER, Importance.FULL_SCREEN, PresentationPath.OVERLAY, null),
            DeliveryPipeline.decide(input()),
        )
    }

    @Test
    public fun alarmWhileLockedIsAFullScreenIntent() {
        val d = DeliveryPipeline.decide(input(Importance.ALARM, device = locked))
        assertEquals(PresentationPath.FULL_SCREEN_INTENT, d.path)
        assertEquals(Importance.ALARM, d.level)
    }

    @Test
    public fun normalIsAlwaysANotification() {
        val d = DeliveryPipeline.decide(input(Importance.NORMAL))
        assertEquals(DeliveryDecision(DeliveryAction.DELIVER, Importance.NORMAL, PresentationPath.NOTIFICATION_ONLY, null), d)
    }

    @Test
    public fun completedItemIsDropped() {
        val d = DeliveryPipeline.decide(input(status = ItemStatus.COMPLETED))
        assertEquals(DeliveryAction.DROP, d.action)
        assertEquals(ReasonCode.ITEM_COMPLETED, d.reasonCode)
    }

    @Test
    public fun deletedItemIsDropped() {
        val d = DeliveryPipeline.decide(input(status = ItemStatus.DELETED))
        assertEquals(DeliveryAction.DROP, d.action)
        assertEquals(ReasonCode.ITEM_DELETED, d.reasonCode)
    }

    @Test
    public fun itemCheckRunsBeforeEverythingElse() {
        // Even with no permissions and a call, a deleted item is reported as deleted.
        val nothingAllowed = inUse.copy(canDrawOverlays = false, notificationsEnabled = false)
        val d = DeliveryPipeline.decide(input(status = ItemStatus.DELETED, device = nothingAllowed, call = true))
        assertEquals(ReasonCode.ITEM_DELETED, d.reasonCode)
    }

    @Test
    public fun missingOverlayPermissionDegradesToANotification() {
        val d = DeliveryPipeline.decide(input(device = inUse.copy(canDrawOverlays = false)))
        assertEquals(DeliveryDecision(DeliveryAction.DELIVER, Importance.NORMAL, PresentationPath.NOTIFICATION_ONLY, ReasonCode.PERMISSION_MISSING), d)
    }

    @Test
    public fun missingFullScreenPermissionWhileLockedDegrades() {
        val d = DeliveryPipeline.decide(input(Importance.ALARM, device = locked.copy(canUseFullScreenIntent = false)))
        assertEquals(Importance.NORMAL, d.level)
        assertEquals(ReasonCode.PERMISSION_MISSING, d.reasonCode)
    }

    @Test
    public fun notificationsOffForNormalMeansNothingCanShow() {
        val d = DeliveryPipeline.decide(input(Importance.NORMAL, device = inUse.copy(notificationsEnabled = false)))
        assertEquals(PresentationPath.NONE, d.path)
        assertEquals(ReasonCode.PERMISSION_MISSING, d.reasonCode)
    }

    @Test
    public fun overlayStillWorksWithNotificationsOff() {
        assertEquals(PresentationPath.OVERLAY, DeliveryPipeline.decide(input(device = inUse.copy(notificationsEnabled = false))).path)
    }

    @Test
    public fun inCallTurnsAPopUpIntoANormalNotification() {
        val d = DeliveryPipeline.decide(input(Importance.ALARM, call = true))
        assertEquals(DeliveryDecision(DeliveryAction.DELIVER, Importance.NORMAL, PresentationPath.NOTIFICATION_ONLY, ReasonCode.IN_CALL), d)
    }

    @Test
    public fun inCallLeavesANormalReminderAlone() {
        val d = DeliveryPipeline.decide(input(Importance.NORMAL, call = true))
        assertNull(d.reasonCode)
        assertEquals(PresentationPath.NOTIFICATION_ONLY, d.path)
    }

    @Test
    public fun inCallAfterADegradeKeepsThePermissionReason() {
        // Already a notification because of the missing permission: the call changes nothing.
        val d = DeliveryPipeline.decide(input(device = inUse.copy(canDrawOverlays = false), call = true))
        assertEquals(ReasonCode.PERMISSION_MISSING, d.reasonCode)
    }

    @Test
    public fun withNoPauseQuietHoursOrFatigueStateEverythingIsDelivered() {
        // Their real rules are tested in DeliveryPipelineCalmTest (Prompt 12).
        listOf(Importance.NORMAL, Importance.FULL_SCREEN, Importance.ALARM).forEach { level ->
            assertEquals(DeliveryAction.DELIVER, DeliveryPipeline.decide(input(level)).action)
        }
        listOf(ReasonCode.PAUSED, ReasonCode.QUIET_HOURS, ReasonCode.FATIGUE_GROUPED, ReasonCode.FATIGUE_DOWNGRADED)
            .forEach { assertEquals(it, ReasonCode.valueOf(it.name)) }
    }
}
