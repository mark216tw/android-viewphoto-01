package com.miniphoto.viewer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerPositionTest {
    @Test
    fun `deleting middle photo selects next photo`() {
        val pending = PendingPhotoDeletion(
            deletedPhotoId = 2L,
            nextPhotoId = 3L,
            previousPhotoId = 1L,
            previousIndex = 1,
        )

        val index = resolveViewerIndex(listOf(1L, 3L, 4L), 2L, 2L, pending)

        assertEquals(1, index)
    }

    @Test
    fun `deleting last photo selects previous photo`() {
        val pending = PendingPhotoDeletion(
            deletedPhotoId = 3L,
            nextPhotoId = null,
            previousPhotoId = 2L,
            previousIndex = 2,
        )

        val index = resolveViewerIndex(listOf(1L, 2L), 3L, 3L, pending)

        assertEquals(1, index)
    }

    @Test
    fun `ordinary refresh preserves current photo by id`() {
        val index = resolveViewerIndex(
            photoIds = listOf(4L, 2L, 1L),
            preferredPhotoId = 2L,
            initialPhotoId = 1L,
            pendingDeletion = null,
        )

        assertEquals(1, index)
    }

    @Test
    fun `missing neighbors clamp previous index`() {
        val pending = PendingPhotoDeletion(
            deletedPhotoId = 3L,
            nextPhotoId = 4L,
            previousPhotoId = 2L,
            previousIndex = 3,
        )

        val index = resolveViewerIndex(listOf(8L, 7L), 3L, 3L, pending)

        assertEquals(1, index)
    }
}
