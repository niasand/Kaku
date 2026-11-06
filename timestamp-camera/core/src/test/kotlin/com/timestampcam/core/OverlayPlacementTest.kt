package com.timestampcam.core

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPlacementTest {
    @Test
    fun uprightSize_swapsAxesForQuarterTurns() {
        assertEquals(100 to 50, OverlayPlacement.uprightSize(100, 50, 0))
        assertEquals(50 to 100, OverlayPlacement.uprightSize(100, 50, 90))
        assertEquals(100 to 50, OverlayPlacement.uprightSize(100, 50, 180))
        assertEquals(50 to 100, OverlayPlacement.uprightSize(100, 50, 270))
    }

    @Test
    fun rotation90_mapsUprightCornersOntoCrop() {
        assertPoint(0f, 50f, 0f, 0f, 90)
        assertPoint(0f, 0f, 50f, 0f, 90)
        assertPoint(100f, 50f, 0f, 100f, 90)
        assertPoint(100f, 0f, 50f, 100f, 90)
    }

    @Test
    fun rotation270_mapsUprightCornersOntoCrop() {
        assertPoint(100f, 0f, 0f, 0f, 270)
        assertPoint(100f, 50f, 50f, 0f, 270)
        assertPoint(0f, 0f, 0f, 100f, 270)
        assertPoint(0f, 50f, 50f, 100f, 270)
    }

    @Test
    fun rotation180_flipsBothAxes() {
        assertPoint(100f, 50f, 0f, 0f, 180)
        assertPoint(0f, 0f, 100f, 50f, 180)
    }

    @Test
    fun mirror_putsTopLeftAtTopRightBeforeRotation() {
        val (x, y) = OverlayPlacement.uprightToBuffer(
            ux = 0f,
            uy = 0f,
            cropLeft = 10f,
            cropTop = 20f,
            cropW = 100f,
            cropH = 50f,
            rotation = 0,
            mirror = true,
        )
        assertEquals(110f, x, 0.01f)
        assertEquals(20f, y, 0.01f)
    }

    private fun assertPoint(expectedX: Float, expectedY: Float, ux: Float, uy: Float, rotation: Int) {
        val (x, y) = OverlayPlacement.uprightToBuffer(
            ux = ux,
            uy = uy,
            cropLeft = 0f,
            cropTop = 0f,
            cropW = 100f,
            cropH = 50f,
            rotation = rotation,
            mirror = false,
        )
        assertEquals(expectedX, x, 0.01f)
        assertEquals(expectedY, y, 0.01f)
    }
}
