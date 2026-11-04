package com.sanjith.betterlife.core

import com.sanjith.betterlife.domain.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The real phone clock. The only place that reads the system time for app logic. */
public class SystemClock : Clock {
    override fun now(): Instant = Instant.now()

    override val zone: ZoneId
        get() = ZoneId.systemDefault()
}

/**
 * Debug-only clock that runs [real] time shifted by an offset, so day rollover, streaks and milestones
 * can be tested by hand. It only changes what app logic thinks "now" is: real AlarmManager alarms
 * still fire at real times. The offset lives in memory, so restarting the app always switches it off.
 * The AppContainer only creates it in debug builds.
 */
public class TimeMachineClock(private val real: Clock) : Clock {

    private val _offset = MutableStateFlow(Duration.ZERO)
    public val offset: StateFlow<Duration> = _offset

    override fun now(): Instant = real.now().plus(_offset.value)

    override val zone: ZoneId
        get() = real.zone

    public fun shift(by: Duration) {
        _offset.value = _offset.value.plus(by)
    }

    public fun setTo(target: Instant) {
        _offset.value = Duration.between(real.now(), target)
    }

    public fun reset() {
        _offset.value = Duration.ZERO
    }
}
