package com.miniphoto.viewer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

internal enum class EditTool { PEN, MARKER, ERASER, BLUR }

internal data class EditStroke(
    val points: List<androidx.compose.ui.geometry.Offset>,
    val color: androidx.compose.ui.graphics.Color,
    val width: Float,
    val tool: EditTool,
    val revision: Int = 0,
)

internal class PhotoEditorViewModel : ViewModel() {
    private var boundPhotoId: Long? = null

    val strokes = mutableStateListOf<EditStroke>()
    val redoStrokes = mutableStateListOf<EditStroke>()
    var currentStroke by mutableStateOf<EditStroke?>(null)
    var tool by mutableStateOf(EditTool.PEN)
    var cropMode by mutableStateOf(false)
    var cropRect by mutableStateOf(androidx.compose.ui.geometry.Rect(.08f, .08f, .92f, .92f))
    var editorInitialViewport by mutableStateOf(PhotoViewport())
    var isDirty by mutableStateOf(false)

    fun bind(photoId: Long, initialViewport: PhotoViewport) {
        if (boundPhotoId == photoId) return
        boundPhotoId = photoId
        strokes.clear()
        redoStrokes.clear()
        currentStroke = null
        tool = EditTool.PEN
        cropMode = false
        cropRect = androidx.compose.ui.geometry.Rect(.08f, .08f, .92f, .92f)
        editorInitialViewport = initialViewport
        isDirty = false
    }

    fun markDirty() {
        isDirty = true
    }

    fun resetAfterTransform() {
        cropMode = false
        cropRect = androidx.compose.ui.geometry.Rect(.08f, .08f, .92f, .92f)
        editorInitialViewport = PhotoViewport()
    }

    fun cancelCrop() {
        cropMode = false
        cropRect = androidx.compose.ui.geometry.Rect(.08f, .08f, .92f, .92f)
    }

    fun markSaved() {
        isDirty = false
    }
}
