package com.sanjith.betterlife.domain.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class SetupStatusMapperTest {

    private val allGranted = DeviceChecks(
        notificationsEnabled = true,
        exactAlarmsAllowed = true,
        canDrawOverlays = true,
        canUseFullScreenIntent = true,
        batteryUnrestricted = true,
    )

    /** All 32 combinations of the five detectable results. */
    private fun allCombinations(): List<DeviceChecks> = (0 until 32).map { bits ->
        DeviceChecks(
            notificationsEnabled = bits and 1 != 0,
            exactAlarmsAllowed = bits and 2 != 0,
            canDrawOverlays = bits and 4 != 0,
            canUseFullScreenIntent = bits and 8 != 0,
            batteryUnrestricted = bits and 16 != 0,
        )
    }

    private fun SetupStatus.item(check: SetupCheck): SetupItem = items.single { it.check == check }

    @Test
    public fun everyCombinationMapsEachDetectableCheckCorrectly() {
        allCombinations().forEach { device ->
            val status = buildSetupStatus(device, manualTicks = emptySet(), isVivo = false)

            assertRequired(status.item(SetupCheck.NOTIFICATIONS), device.notificationsEnabled, SetupReason.NOTIFICATIONS_OFF)
            assertRequired(status.item(SetupCheck.EXACT_ALARMS), device.exactAlarmsAllowed, SetupReason.EXACT_ALARMS_DENIED)
            assertRequired(status.item(SetupCheck.DRAW_OVER_APPS), device.canDrawOverlays, SetupReason.OVERLAY_DENIED)
            assertRequired(
                status.item(SetupCheck.FULL_SCREEN_INTENT),
                device.canUseFullScreenIntent,
                SetupReason.FULL_SCREEN_DENIED,
            )
            val battery = status.item(SetupCheck.BATTERY_UNRESTRICTED)
            if (device.batteryUnrestricted) {
                assertEquals(SetupItem(SetupCheck.BATTERY_UNRESTRICTED, SetupLevel.GREEN, SetupReason.GRANTED), battery)
            } else {
                assertEquals(
                    SetupItem(SetupCheck.BATTERY_UNRESTRICTED, SetupLevel.YELLOW, SetupReason.BATTERY_RESTRICTED),
                    battery,
                )
            }
        }
    }

    @Test
    public fun everyCombinationHasTheWorstLevelAsOverall() {
        allCombinations().forEach { device ->
            val status = buildSetupStatus(device, manualTicks = emptySet(), isVivo = false)
            val anyRequiredMissing = !device.notificationsEnabled || !device.exactAlarmsAllowed ||
                !device.canDrawOverlays || !device.canUseFullScreenIntent
            val expected = when {
                anyRequiredMissing -> SetupLevel.RED
                !device.batteryUnrestricted -> SetupLevel.YELLOW
                else -> SetupLevel.GREEN
            }
            assertEquals("for $device", expected, status.overall)
        }
    }

    @Test
    public fun otherBrandsGetNoVivoRows() {
        val status = buildSetupStatus(allGranted, manualTicks = SetupCheck.MANUAL_CHECKS, isVivo = false)

        assertEquals(5, status.items.size)
        assertTrue(status.items.none { it.check.isManual })
        assertEquals(SetupLevel.GREEN, status.overall)
    }

    @Test
    public fun vivoRowsAreYellowUntilTicked() {
        val status = buildSetupStatus(allGranted, manualTicks = emptySet(), isVivo = true)

        assertEquals(9, status.items.size)
        SetupCheck.MANUAL_CHECKS.forEach { check ->
            assertEquals(SetupItem(check, SetupLevel.YELLOW, SetupReason.NOT_CONFIRMED), status.item(check))
        }
        assertEquals(SetupLevel.YELLOW, status.overall)
    }

    @Test
    public fun tickedVivoRowsAreGreenAndOthersStayYellow() {
        val ticks = setOf(SetupCheck.VIVO_AUTOSTART, SetupCheck.VIVO_RECENTS_LOCK)
        val status = buildSetupStatus(allGranted, manualTicks = ticks, isVivo = true)

        assertEquals(SetupLevel.GREEN, status.item(SetupCheck.VIVO_AUTOSTART).level)
        assertEquals(SetupReason.CONFIRMED_BY_OWNER, status.item(SetupCheck.VIVO_RECENTS_LOCK).reason)
        assertEquals(SetupLevel.YELLOW, status.item(SetupCheck.VIVO_BACKGROUND_POPUPS).level)
        assertEquals(SetupLevel.YELLOW, status.item(SetupCheck.VIVO_LOCK_SCREEN).level)
    }

    @Test
    public fun everythingGrantedAndTickedIsGreen() {
        val status = buildSetupStatus(allGranted, manualTicks = SetupCheck.MANUAL_CHECKS, isVivo = true)

        assertTrue(status.items.all { it.level == SetupLevel.GREEN })
        assertEquals(SetupLevel.GREEN, status.overall)
    }

    @Test
    public fun rowsKeepAStableOrder() {
        val status = buildSetupStatus(allGranted, manualTicks = emptySet(), isVivo = true)

        assertEquals(SetupCheck.entries.toList(), status.items.map { it.check })
    }

    @Test
    public fun redOutranksYellowFromManualRows() {
        val device = allGranted.copy(canDrawOverlays = false)
        val status = buildSetupStatus(device, manualTicks = emptySet(), isVivo = true)

        assertEquals(SetupLevel.RED, status.overall)
    }

    private fun assertRequired(item: SetupItem, granted: Boolean, deniedReason: SetupReason) {
        if (granted) {
            assertEquals(SetupLevel.GREEN, item.level)
            assertEquals(SetupReason.GRANTED, item.reason)
        } else {
            assertEquals(SetupLevel.RED, item.level)
            assertEquals(deniedReason, item.reason)
        }
    }
}
