package com.zhiwei.shiyin.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameClockTest {
    @Test
    fun laterFramesAdvanceFromTheCameraClock() {
        val clock = FrameClock()
        val first = clock.wallClockMillis(5_000_000_000L, 1_700_000_000_000L)
        val second = clock.wallClockMillis(6_000_000_000L, 1_700_000_009_000L)
        assertEquals(1_700_000_000_000L, first)
        assertEquals(1_700_000_001_000L, second)
    }

    @Test
    fun smallJitterDoesNotRecalibrate() {
        val clock = FrameClock()
        clock.wallClockMillis(6_000_000_000L, 1_000L)
        val wall = clock.wallClockMillis(5_900_000_000L, 50_000L)
        assertEquals(900L, wall)
    }

    @Test
    fun aTimestampJumpRecalibratesToTheFallbackClock() {
        val clock = FrameClock()
        clock.wallClockMillis(6_000_000_000L, 1_000L)
        val wall = clock.wallClockMillis(100_000_000_000L, 1_800_000_000_000L)
        assertEquals(1_800_000_000_000L, wall)
    }
}
