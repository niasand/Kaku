package com.zhiwei.shiyin.core

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

fun formatWatermark(epochMs: Long, zone: ZoneId): String {
    val time = Instant.ofEpochMilli(epochMs).atZone(zone)
    return String.format(
        Locale.US,
        "%04d年%02d月%02d日 %02d:%02d:%02d",
        time.year,
        time.monthValue,
        time.dayOfMonth,
        time.hour,
        time.minute,
        time.second,
    )
}

fun recordingId(epochMs: Long, zone: ZoneId): String {
    val time = Instant.ofEpochMilli(epochMs).atZone(zone)
    return String.format(
        Locale.US,
        "%04d%02d%02d_%02d%02d%02d",
        time.year,
        time.monthValue,
        time.dayOfMonth,
        time.hour,
        time.minute,
        time.second,
    )
}

fun formatElapsed(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatSrtTimestamp(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val hours = clamped / 3_600_000L
    val minutes = (clamped % 3_600_000L) / 60_000L
    val seconds = (clamped % 60_000L) / 1_000L
    val millis = clamped % 1_000L
    return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
}
