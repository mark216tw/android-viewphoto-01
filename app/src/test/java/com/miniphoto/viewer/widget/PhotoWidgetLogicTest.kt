package com.miniphoto.viewer.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoWidgetLogicTest {
    @Test
    fun `negative index clamps to first photo`() {
        assertEquals(0, clampPhotoIndex(-3, 5))
    }

    @Test
    fun `index past the end clamps to last photo`() {
        assertEquals(4, clampPhotoIndex(9, 5))
    }

    @Test
    fun `empty photo list clamps to zero`() {
        assertEquals(0, clampPhotoIndex(2, 0))
    }

    @Test
    fun `index inside bounds is unchanged`() {
        assertEquals(2, clampPhotoIndex(2, 5))
    }

    @Test
    fun `next from the last photo stays on the last photo`() {
        assertEquals(4, stepPhotoIndex(4, 5, 1))
    }

    @Test
    fun `previous from the first photo stays on the first photo`() {
        assertEquals(0, stepPhotoIndex(0, 5, -1))
    }

    @Test
    fun `stepping forward moves one photo`() {
        assertEquals(3, stepPhotoIndex(2, 5, 1))
    }

    @Test
    fun `sample size keeps decoded image at or above target`() {
        assertEquals(4, calculateSampleSize(4000, 3000, 512))
        assertEquals(4, calculateSampleSize(2048, 1536, 512))
    }

    @Test
    fun `sample size stays one when no shrinking is needed`() {
        assertEquals(1, calculateSampleSize(640, 480, 512))
        assertEquals(1, calculateSampleSize(1000, 1000, 512))
    }

    @Test
    fun `sample size handles invalid input`() {
        assertEquals(1, calculateSampleSize(0, 480, 512))
        assertEquals(1, calculateSampleSize(640, 480, 0))
    }
}
