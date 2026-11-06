package com.timestampcam.core

/**
 * Maps upright, user-facing coordinates into the camera buffer.
 *
 * CameraX crops, then rotates clockwise by [rotation], then optionally mirrors.
 * Drawing with the inverse of that chain puts the timestamp and subtitle
 * where they appear upright in the preview and in the recorded file.
 */
object OverlayPlacement {
    fun uprightSize(cropW: Int, cropH: Int, rotation: Int): Pair<Int, Int> {
        return if (rotation == 90 || rotation == 270) cropH to cropW else cropW to cropH
    }

    fun uprightToBuffer(
        ux: Float,
        uy: Float,
        cropLeft: Float,
        cropTop: Float,
        cropW: Float,
        cropH: Float,
        rotation: Int,
        mirror: Boolean,
    ): Pair<Float, Float> {
        val viewW = if (rotation == 90 || rotation == 270) cropH else cropW
        val x = if (mirror) viewW - ux else ux
        val (cx, cy) = when (rotation) {
            90 -> uy to (cropH - x)
            180 -> (cropW - x) to (cropH - uy)
            270 -> (cropW - uy) to x
            else -> x to uy
        }
        return (cropLeft + cx) to (cropTop + cy)
    }
}
