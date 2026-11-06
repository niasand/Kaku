package com.zhiwei.shiyin.core

/**
 * Maps a camera frame timestamp onto the wall clock.
 *
 * The first frame (and any later frame whose timestamp jumps by more than two
 * seconds) anchors the offset. Later frames then advance with the camera clock,
 * so a delayed draw callback still stamps the moment the frame was captured.
 */
class FrameClock {
    private var epochOffsetMs: Long? = null
    private var lastTimestampNs: Long = Long.MIN_VALUE

    fun wallClockMillis(frameTimestampNs: Long, fallbackEpochMs: Long): Long {
        val previous = lastTimestampNs
        lastTimestampNs = frameTimestampNs
        val jumped = previous != Long.MIN_VALUE &&
            kotlin.math.abs(frameTimestampNs - previous) > DISCONTINUITY_NS
        val offset = epochOffsetMs
        if (offset == null || jumped) {
            epochOffsetMs = fallbackEpochMs - frameTimestampNs / 1_000_000L
            return fallbackEpochMs
        }
        return frameTimestampNs / 1_000_000L + offset
    }

    private companion object {
        const val DISCONTINUITY_NS = 2_000_000_000L
    }
}
