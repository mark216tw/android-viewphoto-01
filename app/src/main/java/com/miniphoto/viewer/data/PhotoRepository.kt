package com.miniphoto.viewer.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PhotoRepository(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    suspend fun loadPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
        )

        buildList {
            resolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val name = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val width = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val height = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val size = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val taken = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val added = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val bucket = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val mime = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    add(
                        Photo(
                            id = cursor.getLong(id),
                            uri = ContentUris.withAppendedId(collection, cursor.getLong(id)),
                            name = cursor.getString(name) ?: "未命名圖片",
                            width = cursor.getInt(width),
                            height = cursor.getInt(height),
                            size = cursor.getLong(size),
                            dateTakenMillis = cursor.getLong(taken).takeIf { it > 0 }
                                ?: cursor.getLong(added) * 1_000,
                            bucketName = cursor.getString(bucket) ?: "其他",
                            mimeType = cursor.getString(mime) ?: "image/jpeg",
                        )
                    )
                }
            }
        }.sortedWith(
            compareByDescending<Photo> { it.dateTakenMillis }
                .thenByDescending { it.id }
        )
    }

    suspend fun loadExternalPhoto(uri: Uri): Photo = withContext(Dispatchers.IO) {
        var name = uri.lastPathSegment ?: "外部圖片"
        var size = 0L
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        Photo(
            id = uri.toString().hashCode().toLong(),
            uri = uri,
            name = name,
            width = bounds.outWidth.coerceAtLeast(0),
            height = bounds.outHeight.coerceAtLeast(0),
            size = size,
            dateTakenMillis = System.currentTimeMillis(),
            bucketName = "外部圖片",
            mimeType = resolver.getType(uri) ?: "image/*",
        )
    }

    suspend fun saveBitmap(bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "mini_$timestamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MiniPhotoViewer")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val directory = java.io.File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "MiniPhotoViewer",
                ).apply { mkdirs() }
                put(MediaStore.Images.Media.DATA, java.io.File(directory, "mini_$timestamp.jpg").absolutePath)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: error("無法建立圖片檔案")
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream))
            } ?: error("無法開啟輸出檔案")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            }
            uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    suspend fun saveShareBitmap(bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val expiration = System.currentTimeMillis() - SHARE_CACHE_MAX_AGE_MILLIS
        directory.listFiles()?.filter { it.isFile && it.lastModified() < expiration }?.forEach(File::delete)

        val file = File(directory, "mini_share_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream))
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private companion object {
        const val SHARE_CACHE_MAX_AGE_MILLIS = 24 * 60 * 60 * 1_000L
    }
}
