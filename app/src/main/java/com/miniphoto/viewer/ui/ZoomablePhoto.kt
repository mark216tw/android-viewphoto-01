package com.miniphoto.viewer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import coil.compose.AsyncImage
import com.miniphoto.viewer.data.Photo
import kotlin.math.max
import kotlin.math.min

data class PhotoViewport(
    val scale: Float = 1f,
    val centerX: Float = .5f,
    val centerY: Float = .5f,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoomablePhoto(
    photo: Photo,
    viewport: PhotoViewport,
    onViewportChange: (PhotoViewport) -> Unit,
    onTap: () -> Unit,
) {
    var containerSize by remember(photo.id) { mutableStateOf(Size.Zero) }
    val fittedSize = fittedImageSize(photo, containerSize)
    val scale = viewport.scale.coerceIn(1f, 10f)
    val offset = viewport.toOffset(scale, fittedSize, containerSize)
    val currentScale by rememberUpdatedState(scale)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnViewportChange by rememberUpdatedState(onViewportChange)

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 10f)
        val nextOffset = if (nextScale == 1f) {
            Offset.Zero
        } else {
            constrainOffset(offset + panChange, nextScale, fittedSize, containerSize)
        }
        onViewportChange(nextOffset.toViewport(nextScale, fittedSize))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .transformable(
                state = transformState,
                canPan = { scale > 1f },
            )
            .pointerInput(photo.id) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = {
                        currentOnViewportChange(
                            if (currentScale > 1f) PhotoViewport() else PhotoViewport(scale = 2.5f)
                        )
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        )
    }
}

private fun fittedImageSize(photo: Photo, container: Size): Size {
    if (container == Size.Zero || photo.width <= 0 || photo.height <= 0) return container
    val fitScale = min(container.width / photo.width, container.height / photo.height)
    return Size(photo.width * fitScale, photo.height * fitScale)
}

fun PhotoViewport.toOffset(scale: Float, fitted: Size, container: Size): Offset {
    if (scale == 1f || fitted == Size.Zero) return Offset.Zero
    val candidate = Offset(
        (.5f - centerX) * fitted.width * scale,
        (.5f - centerY) * fitted.height * scale,
    )
    return constrainOffset(candidate, scale, fitted, container)
}

private fun Offset.toViewport(scale: Float, fitted: Size): PhotoViewport {
    if (scale == 1f || fitted == Size.Zero) return PhotoViewport()
    return PhotoViewport(
        scale = scale,
        centerX = (.5f - x / (fitted.width * scale)).coerceIn(0f, 1f),
        centerY = (.5f - y / (fitted.height * scale)).coerceIn(0f, 1f),
    )
}

private fun constrainOffset(offset: Offset, scale: Float, fitted: Size, container: Size): Offset {
    val maxX = max(0f, (fitted.width * scale - container.width) / 2f)
    val maxY = max(0f, (fitted.height * scale - container.height) / 2f)
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}
