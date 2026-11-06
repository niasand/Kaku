package com.zhiwei.shiyin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FrameGeometryTest {
    @Test
    fun uprightPointsRoundTripThroughTheCameraPipeline() {
        for (mirror in listOf(false, true)) {
            for (rotation in listOf(0, 90, 180, 270)) {
                val geometry = FrameGeometry(
                    cropLeft = 10,
                    cropTop = 20,
                    cropWidth = 100,
                    cropHeight = 200,
                    rotationDegrees = rotation,
                    mirroring = mirror,
                )
                val xs = listOf(0f, 1f, geometry.uprightWidth / 2f, geometry.uprightWidth.toFloat())
                val ys = listOf(0f, 3f, geometry.uprightHeight / 2f, geometry.uprightHeight.toFloat())
                for (x in xs) {
                    for (y in ys) {
                        val (bx, by) = geometry.uprightToBuffer(x, y)
                        val back = bufferToFinal(geometry, bx, by)
                        assertNotNull("rotation=$rotation mirror=$mirror point=($x,$y)", back)
                        assertEquals(x, back!!.first, 0.02f)
                        assertEquals(y, back.second, 0.02f)
                    }
                }
            }
        }
    }

    @Test
    fun zeroRotationIsTheCropOffset() {
        val geometry = FrameGeometry(10, 20, 100, 200, 0, false)
        assertEquals(15f to 26f, geometry.uprightToBuffer(5f, 6f))
        val mirrored = geometry.copy(mirroring = true)
        assertEquals(105f to 26f, mirrored.uprightToBuffer(5f, 6f))
    }

    @Test
    fun portraitRotationSwapsTheUprightSize() {
        val geometry = FrameGeometry.from(0, 0, 1920, 1080, 90, false)
        assertNotNull(geometry)
        assertEquals(1080, geometry!!.uprightWidth)
        assertEquals(1920, geometry.uprightHeight)
    }

    @Test
    fun oddRotationSnapsToTheNearestRightAngle() {
        val geometry = FrameGeometry.from(0, 0, 100, 80, 80, true)
        assertEquals(90, geometry!!.rotationDegrees)
        assertEquals(true, geometry.mirroring)
    }

    private fun bufferToFinal(geometry: FrameGeometry, bx: Float, by: Float): Pair<Float, Float>? {
        val cx = bx - geometry.cropLeft
        val cy = by - geometry.cropTop
        if (cx < -0.01f || cy < -0.01f || cx > geometry.cropWidth + 0.01f || cy > geometry.cropHeight + 0.01f) {
            return null
        }
        var (ux, uy) = when (geometry.rotationDegrees) {
            0 -> cx to cy
            90 -> (geometry.cropHeight - cy) to cx
            180 -> (geometry.cropWidth - cx) to (geometry.cropHeight - cy)
            270 -> cy to (geometry.cropWidth - cx)
            else -> error("rotation")
        }
        if (geometry.mirroring) ux = geometry.uprightWidth - ux
        return ux to uy
    }
}
