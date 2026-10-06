package com.miniphoto.viewer.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class PhotoPage(
    val photos: List<Photo>,
    val hasMore: Boolean,
)

internal fun effectiveDateMillis(dateTakenMillis: Long, dateAddedSeconds: Long): Long =
    dateTakenMillis.takeIf { it > 0 } ?: dateAddedSeconds * 1_000

internal fun comparePhotoOrder(firstDate: Long, firstId: Long, secondDate: Long, secondId: Long): Int =
    if (firstDate != secondDate) secondDate.compareTo(firstDate) else secondId.compareTo(firstId)

class PhotoRepository(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val photoCollection: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    suspend fun loadPhotos(offset: Int, limit: Int): PhotoPage = withContext(Dispatchers.IO) {
        val collection = photoCollection
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
        )
        val photos = try {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, PHOTO_SORT_ORDER)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit + 1)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            }
            queryPhotos(
                collection,
                projection,
                queryArgs = queryArgs,
                sortOrder = null,
                maxRows = limit + 1,
            )
        } catch (error: RuntimeException) {
            if (error !is IllegalArgumentException && error !is SQLiteException && error !is UnsupportedOperationException) {
                throw error
            }
            queryPhotos(
                collection = collection,
                projection = projection,
                queryArgs = null,
                sortOrder = MediaStore.Images.Media._ID + " DESC",
                maxRows = null,
            ).sortedWith { first, second ->
                comparePhotoOrder(first.dateTakenMillis, first.id, second.dateTakenMillis, second.id)
            }.drop(offset).take(limit + 1)
        }
        PhotoPage(photos = photos.take(limit), hasMore = photos.size > limit)
    }

    private fun queryPhotos(
        collection: Uri,
        projection: Array<String>,
        queryArgs: Bundle?,
        sortOrder: String?,
        maxRows: Int?,
    ): List<Photo> {
        val cursor = if (queryArgs != null) {
            resolver.query(collection, projection, queryArgs, null)
        } else {
            resolver.query(collection, projection, null, null, sortOrder)
        } ?: error("無法查詢圖片")
        val photos = mutableListOf<Photo>()
        cursor.use {
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val takenIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (cursor.moveToNext() && (maxRows == null || photos.size < maxRows)) {
                val id = cursor.getLong(idIndex)
                val bucketName = cursor.getString(bucketIndex)
                photos += Photo(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = cursor.getString(nameIndex) ?: "未命名圖片",
                    width = cursor.getInt(widthIndex),
                    height = cursor.getInt(heightIndex),
                    size = cursor.getLong(sizeIndex),
                    dateTakenMillis = effectiveDateMillis(
                        cursor.getLong(takenIndex),
                        cursor.getLong(addedIndex),
                    ),
                    bucketId = cursor.getString(bucketIdIndex) ?: bucketName ?: "other",
                    bucketName = bucketName ?: "其他",
                    mimeType = cursor.getString(mimeIndex) ?: "image/jpeg",
                )
            }
        }
        return photos
    }

    fun registerPhotoObserver(observer: ContentObserver) {
        resolver.registerContentObserver(photoCollection, true, observer)
    }

    fun unregisterPhotoObserver(observer: ContentObserver) {
        resolver.unregisterContentObserver(observer)
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
            bucketId = "external",
            bucketName = "外部圖片",
            mimeType = resolver.getType(uri) ?: "image/*",
        )
    }

    suspend fun saveBitmap(bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val output = bitmap.outputSpec()
        val displayName = "mini_$timestamp.${output.extension}"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, output.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MiniPhotoViewer")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val directory = java.io.File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "MiniPhotoViewer",
                ).apply { mkdirs() }
                put(MediaStore.Images.Media.DATA, java.io.File(directory, displayName).absolutePath)
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
                check(bitmap.compress(output.format, output.quality, stream))
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

        val output = bitmap.outputSpec()
        val file = File(directory, "mini_share_${System.currentTimeMillis()}.${output.extension}")
        file.outputStream().use { stream ->
            check(bitmap.compress(output.format, output.quality, stream))
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private companion object {
        const val PHOTO_SORT_ORDER = "CASE WHEN ${MediaStore.Images.Media.DATE_TAKEN} IS NOT NULL " +
            "AND ${MediaStore.Images.Media.DATE_TAKEN} > 0 THEN ${MediaStore.Images.Media.DATE_TAKEN} " +
            "ELSE ${MediaStore.Images.Media.DATE_ADDED} * 1000 END DESC, ${MediaStore.Images.Media._ID} DESC"
        const val SHARE_CACHE_MAX_AGE_MILLIS = 24 * 60 * 60 * 1_000L
    }
}

private data class BitmapOutput(
    val extension: String,
    val mimeType: String,
    val format: Bitmap.CompressFormat,
    val quality: Int,
)

private fun Bitmap.outputSpec(): BitmapOutput = if (hasAlpha()) {
    BitmapOutput("png", "image/png", Bitmap.CompressFormat.PNG, 100)
} else {
    BitmapOutput("jpg", "image/jpeg", Bitmap.CompressFormat.JPEG, 95)
}
