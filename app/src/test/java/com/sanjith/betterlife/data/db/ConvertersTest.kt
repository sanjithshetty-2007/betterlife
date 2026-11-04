package com.sanjith.betterlife.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

public class ConvertersTest {

    @Test
    public fun localDateIsStoredAsIsoTextWithoutZone() {
        assertEquals("2026-10-08", Converters.localDateToText(LocalDate.of(2026, 10, 8)))
    }

    @Test
    public fun localDateRoundTripsIncludingLeapDayAndYearEnds() {
        listOf(
            LocalDate.of(2028, 2, 29),
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2027, 1, 1),
            LocalDate.MIN.plusYears(1),
        ).forEach { date ->
            assertEquals(date, Converters.textToLocalDate(Converters.localDateToText(date)))
        }
    }

    @Test
    public fun zoneRoundTripsByRegionId() {
        val zone = ZoneId.of("Asia/Kolkata")
        assertEquals("Asia/Kolkata", Converters.zoneIdToText(zone))
        assertEquals(zone, Converters.textToZoneId("Asia/Kolkata"))
    }

    @Test
    public fun offsetZonesRoundTrip() {
        val zone = ZoneId.of("+05:30")
        assertEquals(zone, Converters.textToZoneId(Converters.zoneIdToText(zone)))
    }

    @Test
    public fun nullsStayNull() {
        assertNull(Converters.localDateToText(null))
        assertNull(Converters.textToLocalDate(null))
        assertNull(Converters.zoneIdToText(null))
        assertNull(Converters.textToZoneId(null))
    }
}
