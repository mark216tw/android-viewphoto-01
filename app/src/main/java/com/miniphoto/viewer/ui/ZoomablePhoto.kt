package com.miniphoto.viewer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
    val scale = viewport.scale.coerceIn(1f, 20f)
    val offset = viewport.toOffset(scale, fittedSize, containerSize)
    val currentScale by rememberUpdatedState(scale)
    val currentViewport by rememberUpdatedState(viewport)
    val currentFittedSize by rememberUpdatedState(fittedSize)
    val currentContainerSize by rememberUpdatedState(containerSize)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnViewportChange by rememberUpdatedState(onViewportChange)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(photo.id) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val gestureFittedSize = currentFittedSize
                    val gestureContainerSize = currentContainerSize
                    var gestureScale = currentViewport.scale.coerceIn(1f, 20f)
                    var gestureOffset = currentViewport.toOffset(
                        gestureScale,
                        gestureFittedSize,
                        gestureContainerSize,
                    )
                    var transforming = false
                    var pastTouchSlop = false
                    var accumulatedPan = Offset.Zero

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            transforming = true
                            val result = calculateZoomTransform(
                                oldScale = gestureScale,
                                oldOffset = gestureOffset,
                                zoomChange = event.calculateZoom(),
                                panChange = event.calculatePan(),
                                centroid = event.calculateCentroid(),
                                fitted = gestureFittedSize,
                                container = gestureContainerSize,
                            )
                            gestureScale = result.scale
                            gestureOffset = result.offset
                            currentOnViewportChange(gestureOffset.toViewport(gestureScale, gestureFittedSize))
                            event.changes.forEach { it.consume() }
                        } else if (pressed.size == 1 && (transforming || gestureScale > 1f)) {
                            val panChange = event.calculatePan()
                            if (transforming || pastTouchSlop) {
                                gestureOffset = constrainOffset(
                                    gestureOffset + panChange,
                                    gestureScale,
                                    gestureFittedSize,
                                    gestureContainerSize,
                                )
                                currentOnViewportChange(gestureOffset.toViewport(gestureScale, gestureFittedSize))
                                event.changes.forEach { it.consume() }
                            } else {
                                accumulatedPan += panChange
                                if (accumulatedPan.getDistance() > touchSlop) {
                                    pastTouchSlop = true
                                    gestureOffset = constrainOffset(
                                        gestureOffset + accumulatedPan,
                                        gestureScale,
                                        gestureFittedSize,
                                        gestureContainerSize,
                                    )
                                    currentOnViewportChange(
                                        gestureOffset.toViewport(gestureScale, gestureFittedSize)
                                    )
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }

                        if (pressed.isEmpty()) break
                    }
                }
            }
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

internal data class ZoomTransformResult(
    val scale: Float,
    val offset: Offset,
)

internal fun calculateZoomTransform(
    oldScale: Float,
    oldOffset: Offset,
    zoomChange: Float,
    panChange: Offset,
    centroid: Offset,
    fitted: Size,
    container: Size,
): ZoomTransformResult {
    val nextScale = (oldScale * zoomChange).coerceIn(1f, 20f)
    if (nextScale == 1f) return ZoomTransformResult(1f, Offset.Zero)
    val appliedZoom = nextScale / oldScale
    val viewportCenter = Offset(container.width / 2f, container.height / 2f)
    val centroidOffset = centroid - viewportCenter
    val candidate = oldOffset * appliedZoom +
        centroidOffset * (1f - appliedZoom) + panChange
    return ZoomTransformResult(
        scale = nextScale,
        offset = constrainOffset(candidate, nextScale, fitted, container),
    )
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

internal fun constrainOffset(offset: Offset, scale: Float, fitted: Size, container: Size): Offset {
    val maxX = max(0f, (fitted.width * scale - container.width) / 2f)
    val maxY = max(0f, (fitted.height * scale - container.height) / 2f)
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}
