package com.sanjith.betterlife.domain.delivery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class TapGuardTest {

    @Test
    public fun tapAtTheMomentOfAppearingIsIgnored() {
        assertFalse(isTapAllowed(shownAtMillis = 1_000, nowMillis = 1_000))
    }

    @Test
    public fun tapJustBeforeTheGuardEndsIsIgnored() {
        assertFalse(isTapAllowed(shownAtMillis = 1_000, nowMillis = 1_699))
    }

    @Test
    public fun tapExactlyAtTheGuardIsAllowed() {
        assertTrue(isTapAllowed(shownAtMillis = 1_000, nowMillis = 1_700))
    }

    @Test
    public fun tapLongAfterIsAllowed() {
        assertTrue(isTapAllowed(shownAtMillis = 1_000, nowMillis = 60_000))
    }

    @Test
    public fun clockBeforeShownTimeIsIgnored() {
        assertFalse(isTapAllowed(shownAtMillis = 1_000, nowMillis = 500))
    }

    @Test
    public fun customGuardIsRespected() {
        assertFalse(isTapAllowed(shownAtMillis = 0, nowMillis = 99, guardMillis = 100))
        assertTrue(isTapAllowed(shownAtMillis = 0, nowMillis = 100, guardMillis = 100))
    }
}
