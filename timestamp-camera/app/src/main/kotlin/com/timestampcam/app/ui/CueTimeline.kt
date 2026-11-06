package com.timestampcam.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.timestampcam.core.Cue

private val Burned = Color(0xFFFFC857)
private val TimelineOnly = Color(0xFF7AD7F0)

@Composable
fun CueTimeline(
    durationMs: Long,
    positionMs: Long,
    cues: List<Cue>,
    onSeek: ((Long) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val span = durationMs.coerceAtLeast(1L)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .then(
                if (onSeek == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(span) {
                        detectDragGestures(
                            onDragStart = { offset -> onSeek(seekTo(offset.x, size.width, span)) },
                            onDrag = { change, _ -> onSeek(seekTo(change.position.x, size.width, span)) },
                        )
                    }
                },
            ),
    ) {
        drawRect(Color(0xFF2A2A2A))
        cues.forEach { cue ->
            val start = cue.startMs.coerceIn(0L, span)
            val end = (if (cue.endMs < 0L) span else cue.endMs).coerceIn(start, span)
            val left = start.toFloat() / span * size.width
            val right = end.toFloat() / span * size.width
            drawRect(
                color = if (cue.burned) Burned else TimelineOnly,
                topLeft = Offset(left, size.height * 0.28f),
                size = Size((right - left).coerceAtLeast(4f), size.height * 0.44f),
            )
        }
        val head = positionMs.coerceIn(0L, span).toFloat() / span * size.width
        drawLine(
            color = Color.White,
            start = Offset(head, 0f),
            end = Offset(head, size.height),
            strokeWidth = 3f,
        )
    }
}

private fun seekTo(x: Float, width: Int, span: Long): Long {
    if (width <= 0) return 0L
    return ((x / width).coerceIn(0f, 1f) * span).toLong()
}
