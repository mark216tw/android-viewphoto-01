package com.miniphoto.viewer.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoEditorViewModelTest {
    @Test
    fun `binding same photo preserves editing session`() {
        val viewModel = PhotoEditorViewModel()
        val viewport = PhotoViewport(scale = 2.5f)
        viewModel.bind(1L, viewport)
        viewModel.strokes.add(
            EditStroke(listOf(Offset(.1f, .2f)), Color.Red, .1f, EditTool.PEN),
        )
        viewModel.markDirty()

        viewModel.bind(1L, PhotoViewport())

        assertEquals(1, viewModel.strokes.size)
        assertTrue(viewModel.isDirty)
        assertEquals(viewport, viewModel.editorInitialViewport)
    }

    @Test
    fun `binding new photo resets editing session`() {
        val viewModel = PhotoEditorViewModel()
        viewModel.bind(1L, PhotoViewport())
        viewModel.strokes.add(
            EditStroke(listOf(Offset(.1f, .2f)), Color.Red, .1f, EditTool.PEN),
        )
        viewModel.redoStrokes.add(
            EditStroke(listOf(Offset(.3f, .4f)), Color.Blue, .1f, EditTool.PEN),
        )
        viewModel.markDirty()

        viewModel.bind(2L, PhotoViewport(scale = 1.5f))

        assertTrue(viewModel.strokes.isEmpty())
        assertTrue(viewModel.redoStrokes.isEmpty())
        assertFalse(viewModel.isDirty)
        assertEquals(PhotoViewport(scale = 1.5f), viewModel.editorInitialViewport)
    }

    @Test
    fun `cancel crop returns to editor and resets crop rectangle`() {
        val viewModel = PhotoEditorViewModel()
        viewModel.bind(1L, PhotoViewport())
        viewModel.cropMode = true
        viewModel.cropRect = Rect(.2f, .2f, .7f, .7f)
        viewModel.markDirty()

        viewModel.cancelCrop()

        assertFalse(viewModel.cropMode)
        assertEquals(Rect(.08f, .08f, .92f, .92f), viewModel.cropRect)
        assertTrue(viewModel.isDirty)
    }
}
