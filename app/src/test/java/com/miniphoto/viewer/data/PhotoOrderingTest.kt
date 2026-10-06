package com.miniphoto.viewer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoOrderingTest {
    @Test
    fun `effective date uses positive taken date`() {
        assertEquals(1_700_000_000_000L, effectiveDateMillis(1_700_000_000_000L, 1_800_000_000L))
    }

    @Test
    fun `effective date falls back to added date in milliseconds`() {
        assertEquals(1_800_000_000_000L, effectiveDateMillis(0L, 1_800_000_000L))
        assertEquals(1_800_000_000_000L, effectiveDateMillis(-1L, 1_800_000_000L))
    }

    @Test
    fun `newer effective date sorts first`() {
        assertTrue(comparePhotoOrder(2_000L, 1L, 1_000L, 2L) < 0)
    }

    @Test
    fun `newer id breaks equal date tie`() {
        assertTrue(comparePhotoOrder(2_000L, 5L, 2_000L, 4L) < 0)
    }
}
