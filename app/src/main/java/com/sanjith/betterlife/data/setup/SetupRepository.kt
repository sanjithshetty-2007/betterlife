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
 * Reminder Health's data (the Setup Checklist before Prompt 11). Combines live permission results with the owner's manual ticks into a [SetupStatus].
 * Permissions can change while the app is in the background, so callers trigger [refresh] on resume.
 */
public class SetupRepository(
    private val checker: DeviceCapabilityChecker,
    private val manualChecks: ManualCheckStore,
    private val healthStore: HealthStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val nowMillis: () -> Long,
) {
    private val deviceChecks = MutableStateFlow<DeviceChecks?>(null)

    /** When Reminder Health last re-read the phone's settings (epoch millis, real time); null = never. */
    public val lastCheckedAt: Flow<Long?> = healthStore.lastCheckedAt

    public val status: Flow<SetupStatus?> = combine(deviceChecks, manualChecks.ticks) { device, ticks ->
        device?.let { buildSetupStatus(it, ticks, checker.isVivo) }
    }

    /** Re-reads every check. Runs on app launch and every time a health screen or Home resumes. */
    public suspend fun refresh() {
        deviceChecks.value = withContext(ioDispatcher) { checker.read() }
        healthStore.setLastCheckedAt(nowMillis())
    }

    public suspend fun setManualTick(check: SetupCheck, ticked: Boolean) {
        manualChecks.setTicked(check, ticked)
    }
}
