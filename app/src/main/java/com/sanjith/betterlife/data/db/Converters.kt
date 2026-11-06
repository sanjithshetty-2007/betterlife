package com.sanjith.betterlife.data.db

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Only these non-primitive column types need converting. Enums use Room's built-in name mapping;
 * instants are plain epoch-millis Longs.
 */
public object Converters {

    /** Calendar dates are stored as ISO text such as 2026-10-08, with no time zone. */
    @TypeConverter
    @JvmStatic
    public fun localDateToText(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    @JvmStatic
    public fun textToLocalDate(text: String?): LocalDate? = text?.let(LocalDate::parse)

    /** Zone ids are stored as region ids such as Asia/Kolkata. */
    @TypeConverter
    @JvmStatic
    public fun zoneIdToText(zone: ZoneId?): String? = zone?.id

    @TypeConverter
    @JvmStatic
    public fun textToZoneId(text: String?): ZoneId? = text?.let(ZoneId::of)

    /** Wall-clock times (a habit's reminder time) are stored as text such as 07:30. */
    @TypeConverter
    @JvmStatic
    public fun localTimeToText(time: LocalTime?): String? = time?.toString()

    @TypeConverter
    @JvmStatic
    public fun textToLocalTime(text: String?): LocalTime? = text?.let(LocalTime::parse)
}
