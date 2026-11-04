package com.sanjith.betterlife.domain.delivery

/** Pop-up buttons ignore taps this long after appearing, so a tap meant for the app underneath is not caught. */
public const val POPUP_TAP_GUARD_MILLIS: Long = 700L

/**
 * True when a tap at [nowMillis] may act on a pop-up shown at [shownAtMillis].
 * Both values come from the same monotonic clock (uptime), so a time change cannot unlock the buttons early.
 *
 * Example: `isTapAllowed(shownAtMillis = 1_000, nowMillis = 1_699)` is false; at 1_700 it is true.
 */
public fun isTapAllowed(
    shownAtMillis: Long,
    nowMillis: Long,
    guardMillis: Long = POPUP_TAP_GUARD_MILLIS,
): Boolean = nowMillis - shownAtMillis >= guardMillis
