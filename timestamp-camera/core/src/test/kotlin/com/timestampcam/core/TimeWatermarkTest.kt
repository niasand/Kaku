package com.timestampcam.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class TimeWatermarkTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test
    fun epochZero_isShanghaiMorning() {
        assertEquals("1970年01月01日", TimeWatermark.formatDate(0L, shanghai))
        assertEquals("08时00分00秒", TimeWatermark.formatTime(0L, shanghai))
        assertEquals("1970年01月01日 08时00分00秒", TimeWatermark.format(0L, shanghai))
    }

    @Test
    fun padsMonthDayAndClock() {
        val ms = 1_793_976_065_000L
        val formatted = TimeWatermark.format(ms, shanghai)
        assertEquals("2026年11月06日 22时41分05秒", formatted)
    }

    @Test
    fun frameClock_advancesWithCaptureTimestamp() {
        val clock = FrameClock()
        assertEquals(1_000L, clock.wallTimeMs(frameTimestampNs = 5_000L, wallNowMs = 1_000L))
        assertEquals(2_000L, clock.wallTimeMs(frameTimestampNs = 1_000_005_000L, wallNowMs = 9_999L))
    }

    @Test
    fun frameClock_resetsWhenTimestampGoesBackwards() {
        val clock = FrameClock()
        clock.wallTimeMs(frameTimestampNs = 50_000_000L, wallNowMs = 10_000L)
        assertEquals(80_000L, clock.wallTimeMs(frameTimestampNs = 10_000L, wallNowMs = 80_000L))
    }

    @Test
    fun durationLabel_includesHoursWhenNeeded() {
        assertEquals("00:00", formatDuration(0))
        assertEquals("01:02", formatDuration(62_000))
        assertEquals("1:01:01", formatDuration(3_661_000))
    }
}
