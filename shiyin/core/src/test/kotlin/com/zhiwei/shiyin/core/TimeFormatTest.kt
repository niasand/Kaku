package com.zhiwei.shiyin.core

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test
    fun watermarkUsesYearMonthDayAnd24HourClock() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val instant = ZonedDateTime.of(2026, 11, 6, 22, 41, 5, 0, shanghai).toInstant()
        assertEquals(
            "2026年11月06日 22:41:05",
            formatWatermark(instant.toEpochMilli(), shanghai),
        )
    }

    @Test
    fun recordingIdMatchesTheWallClock() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val instant = ZonedDateTime.of(2026, 11, 6, 22, 41, 5, 0, shanghai).toInstant()
        assertEquals("20261106_224105", recordingId(instant.toEpochMilli(), shanghai))
    }

    @Test
    fun watermarkPadsSingleDigitFields() {
        val utc = ZoneId.of("UTC")
        val instant = ZonedDateTime.of(2026, 1, 2, 3, 4, 5, 0, utc).toInstant()
        assertEquals("2026年01月02日 03:04:05", formatWatermark(instant.toEpochMilli(), utc))
    }

    @Test
    fun elapsedClockOmitsHoursUntilNeeded() {
        assertEquals("00:00", formatElapsed(0))
        assertEquals("01:02", formatElapsed(62_000))
        assertEquals("1:01:01", formatElapsed(3_661_000))
    }

    @Test
    fun srtTimestampUsesCommaForMillis() {
        assertEquals("00:00:01,250", formatSrtTimestamp(1_250))
        assertEquals("01:02:03,004", formatSrtTimestamp(3_723_004))
    }
}
