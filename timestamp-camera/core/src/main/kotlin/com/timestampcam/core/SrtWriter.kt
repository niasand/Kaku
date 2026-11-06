package com.timestampcam.core

object SrtWriter {
    fun write(cues: List<Cue>, fallbackEndMs: Long): String {
        val closed = cues.map { cue ->
            if (cue.endMs < 0L) {
                cue.copy(endMs = fallbackEndMs.coerceAtLeast(cue.startMs + 1L))
            } else {
                cue
            }
        }.filter { it.endMs > it.startMs }
            .sortedBy { it.startMs }
        return buildString {
            closed.forEachIndexed { index, cue ->
                append(index + 1)
                append('\n')
                append(formatTimestamp(cue.startMs))
                append(" --> ")
                append(formatTimestamp(cue.endMs))
                append('\n')
                append(cue.text.trim())
                append("\n\n")
            }
        }
    }

    fun formatTimestamp(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val hours = total / 3_600_000L
        val minutes = (total % 3_600_000L) / 60_000L
        val seconds = (total % 60_000L) / 1_000L
        val millis = total % 1_000L
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }
}
