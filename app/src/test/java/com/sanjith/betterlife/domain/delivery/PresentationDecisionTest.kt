package com.sanjith.betterlife.domain.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

public class PresentationDecisionTest {

    private val allGranted = PresentationInputs(
        isInteractive = true,
        isKeyguardLocked = false,
        canDrawOverlays = true,
        canUseFullScreenIntent = true,
        notificationsEnabled = true,
    )

    private fun allCombinations(): List<PresentationInputs> = (0 until 32).map { bits ->
        PresentationInputs(
            isInteractive = bits and 1 != 0,
            isKeyguardLocked = bits and 2 != 0,
            canDrawOverlays = bits and 4 != 0,
            canUseFullScreenIntent = bits and 8 != 0,
            notificationsEnabled = bits and 16 != 0,
        )
    }

    @Test
    public fun phoneInUseWithOverlayShowsOverlay() {
        assertEquals(
            PresentationDecision(PresentationPath.OVERLAY, PresentationReason.PHONE_IN_USE),
            decidePresentation(allGranted),
        )
    }

    @Test
    public fun overlayWorksEvenWithNotificationsOff() {
        assertEquals(
            PresentationPath.OVERLAY,
            decidePresentation(allGranted.copy(notificationsEnabled = false)).path,
        )
    }

    @Test
    public fun lockedPhoneUsesFullScreenIntent() {
        assertEquals(
            PresentationDecision(PresentationPath.FULL_SCREEN_INTENT, PresentationReason.LOCKED_OR_SCREEN_OFF),
            decidePresentation(allGranted.copy(isKeyguardLocked = true)),
        )
    }

    @Test
    public fun screenOffUnlockedUsesFullScreenIntent() {
        assertEquals(
            PresentationPath.FULL_SCREEN_INTENT,
            decidePresentation(allGranted.copy(isInteractive = false)).path,
        )
    }

    @Test
    public fun phoneInUseWithoutOverlayFallsBackToNotification() {
        assertEquals(
            PresentationDecision(PresentationPath.NOTIFICATION_ONLY, PresentationReason.OVERLAY_DENIED),
            decidePresentation(allGranted.copy(canDrawOverlays = false)),
        )
    }

    @Test
    public fun lockedWithoutFullScreenPermissionFallsBackToNotification() {
        assertEquals(
            PresentationDecision(PresentationPath.NOTIFICATION_ONLY, PresentationReason.FULL_SCREEN_DENIED),
            decidePresentation(allGranted.copy(isKeyguardLocked = true, canUseFullScreenIntent = false)),
        )
    }

    @Test
    public fun lockedWithNotificationsOffShowsNothing() {
        assertEquals(
            PresentationDecision(PresentationPath.NONE, PresentationReason.NOTIFICATIONS_OFF),
            decidePresentation(allGranted.copy(isKeyguardLocked = true, notificationsEnabled = false)),
        )
    }

    @Test
    public fun overlayIsChosenOnlyWhenPhoneInUseAndAllowed() {
        allCombinations().forEach { inputs ->
            val phoneInUse = inputs.isInteractive && !inputs.isKeyguardLocked
            val isOverlay = decidePresentation(inputs).path == PresentationPath.OVERLAY
            assertEquals("for $inputs", phoneInUse && inputs.canDrawOverlays, isOverlay)
        }
    }

    @Test
    public fun overlayIsNeverUsedOnTheLockScreen() {
        allCombinations().filter { it.isKeyguardLocked }.forEach { inputs ->
            assertNotEquals("for $inputs", PresentationPath.OVERLAY, decidePresentation(inputs).path)
        }
    }

    @Test
    public fun fullScreenIntentNeedsLockedOrOffPlusPermissionAndNotifications() {
        allCombinations().forEach { inputs ->
            val phoneInUse = inputs.isInteractive && !inputs.isKeyguardLocked
            val expected = !phoneInUse && inputs.canUseFullScreenIntent && inputs.notificationsEnabled
            val isFullScreen = decidePresentation(inputs).path == PresentationPath.FULL_SCREEN_INTENT
            assertEquals("for $inputs", expected, isFullScreen)
        }
    }

    @Test
    public fun noneOnlyWhenNoOverlayAndNotificationsOff() {
        allCombinations().forEach { inputs ->
            val phoneInUse = inputs.isInteractive && !inputs.isKeyguardLocked
            val expected = !inputs.notificationsEnabled && !(phoneInUse && inputs.canDrawOverlays)
            val decision = decidePresentation(inputs)
            assertEquals("for $inputs", expected, decision.path == PresentationPath.NONE)
            if (expected) assertEquals(PresentationReason.NOTIFICATIONS_OFF, decision.reason)
        }
    }
}
