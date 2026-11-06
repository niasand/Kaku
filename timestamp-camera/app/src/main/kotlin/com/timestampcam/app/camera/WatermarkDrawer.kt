package com.timestampcam.app.camera

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.camera.effects.Frame
import com.timestampcam.core.FrameClock
import com.timestampcam.core.OverlayPlacement
import com.timestampcam.core.TimeWatermark
import java.time.ZoneId

/**
 * Draws the wall-clock stamp and the active subtitle into a camera frame.
 * [draw] runs once per frame on the overlay thread, so the stamp tracks that frame.
 */
class WatermarkDrawer {
    private val clock = FrameClock()
    private val zone: ZoneId = ZoneId.systemDefault()
    private val matrix = Matrix()
    private val src = FloatArray(6)
    private val dst = FloatArray(6)
    private val box = RectF()

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB3000000.toInt()
        style = Paint.Style.FILL
    }

    private var cachedSubtitle = ""
    private var cachedWidth = 0
    private var cachedSize = 0f
    private var cachedLayout: StaticLayout? = null

    fun draw(frame: Frame, subtitle: String) {
        val canvas = frame.overlayCanvas
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        val crop = frame.cropRect
        if (crop.width() <= 0 || crop.height() <= 0) return

        val (viewW, viewH) = OverlayPlacement.uprightSize(crop.width(), crop.height(), frame.rotationDegrees)
        if (viewW <= 0 || viewH <= 0) return

        val wallMs = clock.wallTimeMs(frame.timestampNanos, System.currentTimeMillis())
        val date = TimeWatermark.formatDate(wallMs, zone)
        val time = TimeWatermark.formatTime(wallMs, zone)

        if (!installUprightMatrix(crop.left.toFloat(), crop.top.toFloat(), crop.width().toFloat(), crop.height().toFloat(), frame.rotationDegrees, frame.isMirroring, viewW, viewH)) {
            drawContents(canvas, viewW, viewH, date, time, subtitle)
            return
        }
        canvas.save()
        canvas.concat(matrix)
        drawContents(canvas, viewW, viewH, date, time, subtitle)
        canvas.restore()
    }

    private fun installUprightMatrix(
        cropLeft: Float,
        cropTop: Float,
        cropW: Float,
        cropH: Float,
        rotation: Int,
        mirror: Boolean,
        viewW: Int,
        viewH: Int,
    ): Boolean {
        val points = arrayOf(
            0f to 0f,
            viewW.toFloat() to 0f,
            0f to viewH.toFloat(),
        )
        points.forEachIndexed { index, (ux, uy) ->
            val (bx, by) = OverlayPlacement.uprightToBuffer(ux, uy, cropLeft, cropTop, cropW, cropH, rotation, mirror)
            src[index * 2] = ux
            src[index * 2 + 1] = uy
            dst[index * 2] = bx
            dst[index * 2 + 1] = by
        }
        return matrix.setPolyToPoly(src, 0, dst, 0, 3)
    }

    private fun drawContents(
        canvas: Canvas,
        viewW: Int,
        viewH: Int,
        date: String,
        time: String,
        subtitle: String,
    ) {
        var textSize = viewH * 0.034f
        timePaint.textSize = textSize
        val maxWidth = viewW * 0.92f
        while (textSize > 18f && maxOf(timePaint.measureText(date), timePaint.measureText(time)) > maxWidth) {
            textSize *= 0.92f
            timePaint.textSize = textSize
        }
        val fm = timePaint.fontMetrics
        val lineH = fm.descent - fm.ascent
        val textW = maxOf(timePaint.measureText(date), timePaint.measureText(time))
        val padX = lineH * 0.45f
        val padY = lineH * 0.28f
        val left = viewW * 0.035f
        val top = viewH * 0.045f
        box.set(left, top, left + textW + padX * 2f, top + lineH * 2f + padY * 2f)
        canvas.drawRoundRect(box, lineH * 0.35f, lineH * 0.35f, bgPaint)
        val x = left + padX
        canvas.drawText(date, x, top + padY - fm.ascent, timePaint)
        canvas.drawText(time, x, top + padY + lineH - fm.ascent, timePaint)

        if (subtitle.isBlank()) return
        val subSize = (viewH * 0.048f).coerceIn(36f, 84f)
        subtitlePaint.textSize = subSize
        val layoutWidth = (viewW * 0.86f).toInt().coerceAtLeast(1)
        val layout = subtitleLayout(subtitle, layoutWidth, subSize)
        val subPad = subSize * 0.38f
        val boxW = layout.width + subPad * 2f
        val boxH = layout.height + subPad * 2f
        val subLeft = (viewW - boxW) / 2f
        val subTop = (viewH * 0.70f).coerceAtMost(viewH - boxH - viewH * 0.04f)
        box.set(subLeft, subTop, subLeft + boxW, subTop + boxH)
        canvas.drawRoundRect(box, subSize * 0.3f, subSize * 0.3f, bgPaint)
        canvas.save()
        canvas.translate(subLeft + subPad, subTop + subPad)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun subtitleLayout(text: String, width: Int, textSize: Float): StaticLayout {
        val cached = cachedLayout
        if (cached != null && cachedSubtitle == text && cachedWidth == width && cachedSize == textSize) {
            return cached
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, subtitlePaint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        cachedSubtitle = text
        cachedWidth = width
        cachedSize = textSize
        cachedLayout = layout
        return layout
    }
}
