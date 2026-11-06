package com.timestampcam.core

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Cue(
    val id: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val burned: Boolean,
)

object SubtitleTimeline {
    const val PLAYBACK_CUE_MS = 3_000L

    fun tapWhileRecording(cues: List<Cue>, text: String, atMs: Long, id: String): Pair<List<Cue>, String?> {
        val position = atMs.coerceAtLeast(0L)
        val open = cues.lastOrNull { it.endMs < 0L }
        if (open != null && open.text == text) {
            return closeOpen(cues, position) to null
        }
        val closed = closeOpen(cues, position)
        val added = closed + Cue(
            id = id,
            text = text,
            startMs = position,
            endMs = -1L,
            burned = true,
        )
        return added.sortedBy { it.startMs } to text
    }

    fun closeOpen(cues: List<Cue>, atMs: Long): List<Cue> {
        val position = atMs.coerceAtLeast(0L)
        return cues.map { cue ->
            if (cue.endMs < 0L) cue.copy(endMs = position.coerceAtLeast(cue.startMs)) else cue
        }.filter { it.endMs < 0L || it.endMs > it.startMs }
    }

    fun insertPlaybackCue(
        cues: List<Cue>,
        text: String,
        atMs: Long,
        durationMs: Long = PLAYBACK_CUE_MS,
        id: String,
    ): List<Cue> {
        val start = atMs.coerceAtLeast(0L)
        val end = start + durationMs.coerceAtLeast(1L)
        val next = mutableListOf<Cue>()
        for (cue in cues) {
            if (cue.burned || cue.endMs < 0L) {
                next += cue
                continue
            }
            val cueEnd = cue.endMs
            when {
                cueEnd <= start || cue.startMs >= end -> next += cue
                cue.startMs < start && cueEnd > end -> {
                    next += cue.copy(endMs = start)
                    next += cue.copy(id = cue.id + "#r", startMs = end, endMs = cueEnd)
                }
                cue.startMs < start -> next += cue.copy(endMs = start)
                cueEnd > end -> next += cue.copy(startMs = end)
            }
        }
        next += Cue(id = id, text = text, startMs = start, endMs = end, burned = false)
        return next
            .filter { it.endMs < 0L || it.endMs > it.startMs }
            .sortedWith(compareBy({ it.startMs }, { it.id }))
    }

    fun remove(cues: List<Cue>, id: String): List<Cue> = cues.filterNot { it.id == id }

    fun activeAt(cues: List<Cue>, timeMs: Long): List<Cue> {
        return cues.filter { cue ->
            cue.startMs <= timeMs && (cue.endMs < 0L || timeMs < cue.endMs)
        }
    }

    fun newId(): String = UUID.randomUUID().toString()
}
