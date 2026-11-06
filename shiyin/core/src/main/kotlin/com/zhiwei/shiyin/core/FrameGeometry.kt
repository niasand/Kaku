package com.zhiwei.shiyin.core

/**
 * CameraX draws the overlay in buffer space, then crops, rotates clockwise, and
 * optionally mirrors. [uprightToBuffer] is the inverse, so a stamp placed in the
 * upright image lands on the pixels that remain readable after that pipeline.
 */
data class FrameGeometry(
    val cropLeft: Int,
    val cropTop: Int,
    val cropWidth: Int,
    val cropHeight: Int,
    val rotationDegrees: Int,
    val mirroring: Boolean,
) {
    val uprightWidth: Int =
        if (rotationDegrees == 90 || rotationDegrees == 270) cropHeight else cropWidth

    val uprightHeight: Int =
        if (rotationDegrees == 90 || rotationDegrees == 270) cropWidth else cropHeight

    fun uprightToBuffer(x: Float, y: Float): Pair<Float, Float> {
        val ux = if (mirroring) uprightWidth - x else x
        val uy = y
        val (cx, cy) = when (rotationDegrees) {
            0 -> ux to uy
            90 -> uy to (cropHeight - ux)
            180 -> (cropWidth - ux) to (cropHeight - uy)
            270 -> (cropWidth - uy) to ux
            else -> ux to uy
        }
        return (cropLeft + cx) to (cropTop + cy)
    }

    companion object {
        fun from(
            cropLeft: Int,
            cropTop: Int,
            cropWidth: Int,
            cropHeight: Int,
            rotationDegrees: Int,
            mirroring: Boolean,
        ): FrameGeometry? {
            if (cropWidth <= 0 || cropHeight <= 0) return null
            val normalized = ((rotationDegrees % 360) + 360) % 360
            val snapped = when (normalized) {
                in 45..134 -> 90
                in 135..224 -> 180
                in 225..314 -> 270
                else -> 0
            }
            return FrameGeometry(cropLeft, cropTop, cropWidth, cropHeight, snapped, mirroring)
        }
    }
}
