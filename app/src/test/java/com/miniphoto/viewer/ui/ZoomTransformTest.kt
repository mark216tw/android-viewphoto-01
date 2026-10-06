package com.miniphoto.viewer.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomTransformTest {
    private val container = Size(1_000f, 1_000f)
    private val fitted = Size(1_000f, 1_000f)

    @Test
    fun `centered zoom scales existing pan`() {
        val result = calculateZoomTransform(
            oldScale = 2f,
            oldOffset = Offset(100f, -50f),
            zoomChange = 1.5f,
            panChange = Offset.Zero,
            centroid = Offset(500f, 500f),
            fitted = fitted,
            container = container,
        )

        assertEquals(3f, result.scale, .001f)
        assertEquals(150f, result.offset.x, .001f)
        assertEquals(-75f, result.offset.y, .001f)
    }

    @Test
    fun `off center zoom keeps content under centroid`() {
        val result = calculateZoomTransform(
            oldScale = 1f,
            oldOffset = Offset.Zero,
            zoomChange = 2f,
            panChange = Offset.Zero,
            centroid = Offset(600f, 500f),
            fitted = fitted,
            container = container,
        )

        assertEquals(2f, result.scale, .001f)
        assertEquals(-100f, result.offset.x, .001f)
        assertEquals(0f, result.offset.y, .001f)
    }

    @Test
    fun `zoom uses clamped applied ratio at maximum`() {
        val result = calculateZoomTransform(
            oldScale = 19f,
            oldOffset = Offset(190f, 0f),
            zoomChange = 2f,
            panChange = Offset.Zero,
            centroid = Offset(500f, 500f),
            fitted = fitted,
            container = container,
        )

        assertEquals(20f, result.scale, .001f)
        assertEquals(200f, result.offset.x, .001f)
    }

    @Test
    fun `zooming down to minimum recenters image`() {
        val result = calculateZoomTransform(
            oldScale = 2f,
            oldOffset = Offset(200f, -100f),
            zoomChange = .1f,
            panChange = Offset(40f, 40f),
            centroid = Offset(700f, 300f),
            fitted = fitted,
            container = container,
        )

        assertEquals(1f, result.scale, .001f)
        assertEquals(Offset.Zero, result.offset)
    }
}
