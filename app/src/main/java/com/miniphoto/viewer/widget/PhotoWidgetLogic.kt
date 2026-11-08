package com.miniphoto.viewer.widget

import kotlin.math.max

internal fun clampPhotoIndex(index: Int, photoCount: Int): Int = when {
    photoCount <= 0 -> 0
    index < 0 -> 0
    index >= photoCount -> photoCount - 1
    else -> index
}

internal fun stepPhotoIndex(index: Int, photoCount: Int, step: Int): Int =
    clampPhotoIndex(index + step, photoCount)

internal fun calculateSampleSize(width: Int, height: Int, targetEdgePx: Int): Int {
    if (width <= 0 || height <= 0 || targetEdgePx <= 0) return 1
    val maxEdge = max(width, height)
    var sample = 1
    while (maxEdge / (sample * 2) >= targetEdgePx) {
        sample *= 2
    }
    return sample
}
