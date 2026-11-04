package com.sanjith.betterlife.data.setup

import com.sanjith.betterlife.domain.setup.DeviceChecks
import com.sanjith.betterlife.domain.setup.SetupCheck
import com.sanjith.betterlife.domain.setup.SetupStatus
import com.sanjith.betterlife.domain.setup.buildSetupStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/**
 * Combines live permission results with the owner's manual ticks into a [SetupStatus].
 * Permissions can change while the app is in the background, so callers trigger [refresh] on resume.
 */
public class SetupRepository(
    private val checker: DeviceCapabilityChecker,
    private val manualChecks: ManualCheckStore,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val deviceChecks = MutableStateFlow<DeviceChecks?>(null)

    public val status: Flow<SetupStatus?> = combine(deviceChecks, manualChecks.ticks) { device, ticks ->
        device?.let { buildSetupStatus(it, ticks, checker.isVivo) }
    }

    public suspend fun refresh() {
        deviceChecks.value = withContext(ioDispatcher) { checker.read() }
    }

    public suspend fun setManualTick(check: SetupCheck, ticked: Boolean) {
        manualChecks.setTicked(check, ticked)
    }
}
