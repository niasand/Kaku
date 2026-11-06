package com.zhiwei.shiyin.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.util.Log
import androidx.camera.effects.Frame
import com.zhiwei.shiyin.core.FrameClock
import com.zhiwei.shiyin.core.FrameGeometry
import com.zhiwei.shiyin.core.formatWatermark
import java.time.ZoneId
import kotlin.math.min

/**
 * Burns the wall-clock time into each camera frame. The stamp is drawn in upright
 * pixels, then mapped through [FrameGeometry] so crop, rotation, and mirror leave
 * it readable in both the preview and the recorded file.
 */
internal class TimestampDrawer(private val clock: FrameClock) {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val stamp = TimestampStamp()
    private val matrix = Matrix()
    private val src = FloatArray(8)
    private val dst = FloatArray(8)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var loggedGeometry = false

    fun draw(frame: Frame) {
        val crop = frame.cropRect
        val geometry = FrameGeometry.from(
            cropLeft = crop.left,
            cropTop = crop.top,
            cropWidth = crop.width(),
            cropHeight = crop.height(),
            rotationDegrees = frame.rotationDegrees,
            mirroring = frame.isMirroring,
        ) ?: return
        if (!loggedGeometry) {
            loggedGeometry = true
            Log.i(
                TAG,
                "overlay size=${frame.size} crop=$crop rotation=${frame.rotationDegrees} mirror=${frame.isMirroring}",
            )
        }
        val canvas = frame.overlayCanvas
        canvas.save()
        canvas.setMatrix(null)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val wall = clock.wallClockMillis(frame.timestampNanos, System.currentTimeMillis())
        val text = formatWatermark(wall, zone)
        val minDim = min(geometry.uprightWidth, geometry.uprightHeight).toFloat()
        var textSize = minDim * 0.046f
        var bitmap = stamp.render(text, textSize)
        val maxWidth = geometry.uprightWidth * 0.94f
        if (bitmap.width > maxWidth && bitmap.width > 0) {
            textSize *= maxWidth / bitmap.width.toFloat()
            bitmap = stamp.render(text, textSize)
        }
        drawMapped(canvas, geometry, bitmap, margin = minDim * 0.04f)
        canvas.restore()
    }

    private fun drawMapped(canvas: Canvas, geometry: FrameGeometry, bitmap: Bitmap, margin: Float) {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val corners = arrayOf(
            geometry.uprightToBuffer(margin, margin),
            geometry.uprightToBuffer(margin + width, margin),
            geometry.uprightToBuffer(margin + width, margin + height),
            geometry.uprightToBuffer(margin, margin + height),
        )
        src[0] = 0f
        src[1] = 0f
        src[2] = width
        src[3] = 0f
        src[4] = width
        src[5] = height
        src[6] = 0f
        src[7] = height
        corners.forEachIndexed { index, (x, y) ->
            dst[index * 2] = x
            dst[index * 2 + 1] = y
        }
        val mapped = matrix.setPolyToPoly(src, 0, dst, 0, 4)
        if (mapped) {
            canvas.drawBitmap(bitmap, matrix, bitmapPaint)
        } else {
            canvas.drawBitmap(bitmap, margin, margin, bitmapPaint)
        }
    }

    private companion object {
        const val TAG = "Shiyin"
    }
}

private class TimestampStamp {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 8, 12, 10)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(242, 193, 78)
    }
    private var bitmap: Bitmap? = null
    private var key: String? = null

    fun render(text: String, textSizePx: Float): Bitmap {
        val cacheKey = "$text@${textSizePx.toInt()}"
        val cached = bitmap
        if (cached != null && key == cacheKey && !cached.isRecycled) return cached
        textPaint.textSize = textSizePx
        val padX = textSizePx * 0.62f
        val padY = textSizePx * 0.34f
        val textWidth = textPaint.measureText(text)
        val metrics = textPaint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val width = (textWidth + padX * 2).toInt().coerceAtLeast(1)
        val height = (textHeight + padY * 2).toInt().coerceAtLeast(1)
        val created = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(created)
        val radius = height * 0.22f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, platePaint)
        canvas.drawRect(0f, 0f, textSizePx * 0.16f, height.toFloat(), accentPaint)
        canvas.drawText(text, padX, padY - metrics.ascent, textPaint)
        bitmap = created
        key = cacheKey
        return created
    }
}
