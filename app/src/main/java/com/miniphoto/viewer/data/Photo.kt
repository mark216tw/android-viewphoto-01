package com.miniphoto.viewer.data

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val width: Int,
    val height: Int,
    val size: Long,
    val dateTakenMillis: Long,
    val bucketId: String,
    val bucketName: String,
    val mimeType: String,
)
