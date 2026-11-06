package com.timestampcam.core

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Wall-clock label burned into each video frame.
 * Date and clock are split so the stamp fits a portrait frame.
 */
object TimeWatermark {
    fun formatDate(epochMs: Long, zone: ZoneId): String {
        val date = Instant.ofEpochMilli(epochMs).atZone(zone)
        return String.format(
            Locale.CHINA,
            "%d年%02d月%02d日",
            date.year,
            date.monthValue,
            date.dayOfMonth,
        )
    }

    fun formatTime(epochMs: Long, zone: ZoneId): String {
        val date = Instant.ofEpochMilli(epochMs).atZone(zone)
        return String.format(
            Locale.CHINA,
            "%02d时%02d分%02d秒",
            date.hour,
            date.minute,
            date.second,
        )
    }

    fun format(epochMs: Long, zone: ZoneId): String {
        return formatDate(epochMs, zone) + " " + formatTime(epochMs, zone)
    }
}

/**
 * Maps a camera frame timestamp onto wall-clock time.
 * The first frame anchors "now"; later frames add the capture timestamp delta,
 * so each frame shows the time it was captured even if drawing is a little late.
 */
class FrameClock {
    private var originTimestampNs: Long = Long.MIN_VALUE
    private var originWallMs: Long = 0L

    fun wallTimeMs(frameTimestampNs: Long, wallNowMs: Long): Long {
        if (originTimestampNs == Long.MIN_VALUE || frameTimestampNs < originTimestampNs) {
            originTimestampNs = frameTimestampNs
            originWallMs = wallNowMs
            return wallNowMs
        }
        return originWallMs + (frameTimestampNs - originTimestampNs) / 1_000_000L
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
