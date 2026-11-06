package com.zhiwei.shiyin.core

import kotlinx.serialization.Serializable

@Serializable
data class SubtitleCue(
    val startMs: Long,
    val text: String,
)

@Serializable
data class SubtitlePreset(
    val id: String,
    val text: String,
)

object SubtitleTimeline {
    fun cleanText(text: String): String = text.replace(Regex("[\\r\\n]+"), " ").trim()

    fun sorted(cues: List<SubtitleCue>): List<SubtitleCue> =
        cues
            .map { it.copy(startMs = it.startMs.coerceAtLeast(0L), text = cleanText(it.text)) }
            .sortedBy { it.startMs }

    fun addCue(
        cues: List<SubtitleCue>,
        positionMs: Long,
        text: String,
        replaceWindowMs: Long = 250L,
    ): List<SubtitleCue> {
        val cleaned = cleanText(text)
        val at = positionMs.coerceAtLeast(0L)
        val current = sorted(cues)
        val index = current.indexOfFirst { kotlin.math.abs(it.startMs - at) <= replaceWindowMs }
        val next = if (index >= 0) {
            current.toMutableList().apply { this[index] = this[index].copy(text = cleaned) }
        } else {
            current + SubtitleCue(at, cleaned)
        }
        return sorted(next)
    }

    fun removeCue(cues: List<SubtitleCue>, startMs: Long): List<SubtitleCue> =
        sorted(cues).filterNot { it.startMs == startMs }

    fun clearAt(cues: List<SubtitleCue>, positionMs: Long): List<SubtitleCue> {
        if (subtitleAt(cues, positionMs) == null) return sorted(cues)
        return addCue(cues, positionMs, "", replaceWindowMs = 0L)
    }

    /** Last cue at or before [positionMs]. A blank cue ends the previous subtitle. */
    fun subtitleAt(cues: List<SubtitleCue>, positionMs: Long): String? {
        val text = sorted(cues).lastOrNull { it.startMs <= positionMs }?.text.orEmpty()
        return text.ifEmpty { null }
    }

    fun toSrt(cues: List<SubtitleCue>, durationMs: Long): String {
        val sorted = sorted(cues)
        val blocks = mutableListOf<String>()
        var index = 1
        for (i in sorted.indices) {
            val cue = sorted[i]
            if (cue.text.isEmpty()) continue
            val nextStart = sorted.getOrNull(i + 1)?.startMs
            val end = when {
                nextStart != null && nextStart > cue.startMs -> nextStart
                durationMs > cue.startMs -> durationMs
                else -> cue.startMs + 1_000L
            }
            if (end <= cue.startMs) continue
            blocks += buildString {
                append(index++)
                append('\n')
                append(formatSrtTimestamp(cue.startMs))
                append(" --> ")
                append(formatSrtTimestamp(end))
                append('\n')
                append(cue.text)
            }
        }
        if (blocks.isEmpty()) return ""
        return blocks.joinToString(separator = "\n\n") + "\n"
    }
}

val DefaultPresets = listOf(
    "开始",
    "检查正常",
    "发现异常",
    "处理完成",
    "结束",
)
