package com.miniphoto.viewer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import androidx.exifinterface.media.ExifInterface
import com.miniphoto.viewer.MainActivity
import com.miniphoto.viewer.R
import com.miniphoto.viewer.data.PhotoRepository
import com.miniphoto.viewer.util.hasImageReadPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

private val widgetUpdateMutex = Mutex()
private const val CONTROLS_VISIBLE_MILLIS = 4_000L

private object WidgetControlsAutoHide {
    private val handler = Handler(Looper.getMainLooper())
    private val tasks = mutableMapOf<Int, Runnable>()

    fun schedule(context: Context, appWidgetId: Int) {
        val appContext = context.applicationContext
        handler.post {
            tasks.remove(appWidgetId)?.let(handler::removeCallbacks)
            val task = Runnable {
                tasks.remove(appWidgetId)
                appContext.sendBroadcast(
                    Intent(appContext, PhotoWidgetProvider::class.java).apply {
                        action = PhotoWidgetProvider.ACTION_HIDE_CONTROLS
                        putExtra(PhotoWidgetProvider.EXTRA_APP_WIDGET_ID, appWidgetId)
                    },
                )
            }
            tasks[appWidgetId] = task
            handler.postDelayed(task, CONTROLS_VISIBLE_MILLIS)
        }
    }

    fun cancel(appWidgetId: Int) {
        handler.post { tasks.remove(appWidgetId)?.let(handler::removeCallbacks) }
    }
}

class PhotoWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NEXT, ACTION_PREVIOUS, ACTION_REFRESH -> {
                updateAsync(context) { handleAction(context, intent) }
            }

            ACTION_SHOW_CONTROLS -> {
                updateAsync(context) { showControls(context, intent) }
            }

            ACTION_HIDE_CONTROLS -> {
                updateAsync(context) { hideControls(context, intent) }
            }

            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                val appWidgetIds = intent
                    .getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    ?: intArrayOf()
                updateAsync(context) {
                    appWidgetIds.forEach { appWidgetId ->
                        PhotoWidgetStateStore(context).setControlsVisible(appWidgetId, false)
                        WidgetControlsAutoHide.cancel(appWidgetId)
                        renderWidget(context, appWidgetId, resetIndex = false)
                    }
                }
            }

            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                    super.onReceive(context, intent)
                } else {
                    updateAsync(context) {
                        PhotoWidgetStateStore(context).setControlsVisible(appWidgetId, false)
                        WidgetControlsAutoHide.cancel(appWidgetId)
                        renderWidget(context, appWidgetId, resetIndex = false)
                    }
                }
            }

            AppWidgetManager.ACTION_APPWIDGET_DELETED -> {
                intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    ?.forEach { PhotoWidgetStateStore(context).remove(it) }
                super.onReceive(context, intent)
            }

            else -> super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach {
            PhotoWidgetStateStore(context).remove(it)
            WidgetControlsAutoHide.cancel(it)
        }
    }

    private fun updateAsync(context: Context, block: suspend () -> Unit) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                widgetUpdateMutex.withLock { block() }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAction(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            EXTRA_APP_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        PhotoWidgetStateStore(context).setControlsVisible(appWidgetId, true)
        when (intent.action) {
            ACTION_REFRESH -> renderWidget(context, appWidgetId, resetIndex = true)
            ACTION_NEXT -> {
                stepStoredIndex(context, appWidgetId, 1)
                renderWidget(context, appWidgetId, resetIndex = false)
            }

            ACTION_PREVIOUS -> {
                stepStoredIndex(context, appWidgetId, -1)
                renderWidget(context, appWidgetId, resetIndex = false)
            }
        }
        WidgetControlsAutoHide.schedule(context, appWidgetId)
    }

    private suspend fun showControls(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            EXTRA_APP_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        PhotoWidgetStateStore(context).setControlsVisible(appWidgetId, true)
        renderWidget(context, appWidgetId, resetIndex = false)
        WidgetControlsAutoHide.schedule(context, appWidgetId)
    }

    private suspend fun hideControls(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            EXTRA_APP_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        PhotoWidgetStateStore(context).setControlsVisible(appWidgetId, false)
        WidgetControlsAutoHide.cancel(appWidgetId)
        renderWidget(context, appWidgetId, resetIndex = false)
    }

    private fun stepStoredIndex(context: Context, appWidgetId: Int, step: Int) {
        val store = PhotoWidgetStateStore(context)
        store.setPhotoIndex(
            appWidgetId,
            stepPhotoIndex(
                index = store.photoIndex(appWidgetId),
                photoCount = WIDGET_PHOTO_LIMIT + 1,
                step = step,
            ),
        )
    }

    private suspend fun renderWidget(
        context: Context,
        appWidgetId: Int,
        resetIndex: Boolean,
    ) {
        val manager = AppWidgetManager.getInstance(context)
        if (appWidgetId !in manager.getAppWidgetIds(widgetComponent(context))) return

        val store = PhotoWidgetStateStore(context)
        val controlsVisible = store.controlsVisible(appWidgetId)
        val views = RemoteViews(context.packageName, R.layout.photo_widget)
        views.setOnClickPendingIntent(
            R.id.widget_root,
            openAppPendingIntent(context, appWidgetId),
        )
        views.setOnClickPendingIntent(
            R.id.widget_controls,
            actionPendingIntent(context, appWidgetId, ACTION_SHOW_CONTROLS),
        )
        views.setOnClickPendingIntent(
            R.id.widget_previous,
            actionPendingIntent(context, appWidgetId, ACTION_PREVIOUS),
        )
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            actionPendingIntent(context, appWidgetId, ACTION_REFRESH),
        )
        views.setOnClickPendingIntent(
            R.id.widget_next,
            actionPendingIntent(context, appWidgetId, ACTION_NEXT),
        )

        if (resetIndex) store.setPhotoIndex(appWidgetId, 0)

        if (!context.hasImageReadPermission()) {
            store.setPhotoIndex(appWidgetId, 0)
            showMessage(
                views = views,
                message = context.getString(R.string.widget_permission_required),
                controlsRowVisible = false,
                controlsVisible = false,
            )
            manager.updateAppWidget(appWidgetId, views)
            return
        }

        val photos = try {
            PhotoRepository(context).loadPhotos(offset = 0, limit = WIDGET_PHOTO_LIMIT).photos
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        }

        if (photos == null) {
            showMessage(
                views = views,
                message = context.getString(R.string.widget_load_error),
                controlsRowVisible = true,
                controlsVisible = controlsVisible,
            )
            manager.updateAppWidget(appWidgetId, views)
            return
        }

        if (photos.isEmpty()) {
            store.setPhotoIndex(appWidgetId, 0)
            showMessage(
                views = views,
                message = context.getString(R.string.widget_empty),
                controlsRowVisible = true,
                controlsVisible = controlsVisible,
            )
            manager.updateAppWidget(appWidgetId, views)
            return
        }

        val index = clampPhotoIndex(
            index = if (resetIndex) 0 else store.photoIndex(appWidgetId),
            photoCount = photos.size,
        )
        store.setPhotoIndex(appWidgetId, index)
        val photo = photos[index]
        val bitmap = loadWidgetBitmap(
            context = context,
            uri = photo.uri,
            targetEdgePx = widgetTargetEdgePx(context, manager, appWidgetId),
        )

        if (bitmap == null) {
            showMessage(
                views = views,
                message = context.getString(R.string.widget_load_error),
                controlsRowVisible = true,
                controlsVisible = controlsVisible,
            )
        } else {
            views.setImageViewBitmap(R.id.widget_image, bitmap)
            views.setViewVisibility(R.id.widget_image, View.VISIBLE)
            views.setViewVisibility(R.id.widget_message, View.GONE)
            views.setViewVisibility(R.id.widget_controls, View.VISIBLE)
            applyControlsVisibility(views, controlsVisible)
            views.setContentDescription(
                R.id.widget_image,
                context.getString(R.string.widget_photo_content_description, photo.name),
            )
            views.setBoolean(R.id.widget_previous, "setEnabled", index > 0)
            views.setBoolean(R.id.widget_next, "setEnabled", index < photos.lastIndex)
        }
        manager.updateAppWidget(appWidgetId, views)
    }

    private fun showMessage(
        views: RemoteViews,
        message: String,
        controlsRowVisible: Boolean,
        controlsVisible: Boolean,
    ) {
        views.setViewVisibility(R.id.widget_image, View.GONE)
        views.setViewVisibility(R.id.widget_message, View.VISIBLE)
        views.setTextViewText(R.id.widget_message, message)
        views.setViewVisibility(
            R.id.widget_controls,
            if (controlsRowVisible) View.VISIBLE else View.GONE,
        )
        applyControlsVisibility(views, controlsVisible)
        views.setBoolean(R.id.widget_previous, "setEnabled", false)
        views.setBoolean(R.id.widget_next, "setEnabled", false)
    }

    private fun applyControlsVisibility(views: RemoteViews, visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.INVISIBLE
        views.setViewVisibility(R.id.widget_previous, visibility)
        views.setViewVisibility(R.id.widget_refresh, visibility)
        views.setViewVisibility(R.id.widget_next, visibility)
    }

    private fun widgetTargetEdgePx(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
    ): Int {
        val options = manager.getAppWidgetOptions(appWidgetId)
        val widthDp = options.getInt(
            AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_WIDGET_DP),
        )
        val heightDp = options.getInt(
            AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_WIDGET_DP),
        )
        val edgePx = (max(widthDp, heightDp) * context.resources.displayMetrics.density).toInt()
        return edgePx.coerceIn(MIN_BITMAP_EDGE_PX, MAX_BITMAP_EDGE_PX)
    }

    private fun loadWidgetBitmap(context: Context, uri: Uri, targetEdgePx: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val hasBounds = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
                bounds.outWidth > 0 && bounds.outHeight > 0
            } ?: false
        }.getOrDefault(false)
        if (!hasBounds) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, targetEdgePx)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() ?: return null

        val oriented = applyExifOrientation(resolver, uri, decoded)
        return scaleToMaxEdge(oriented, MAX_BITMAP_EDGE_PX)
    }

    private fun applyExifOrientation(
        resolver: ContentResolver,
        uri: Uri,
        bitmap: Bitmap,
    ): Bitmap {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }

        val rotated = runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrNull() ?: return bitmap
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleToMaxEdge(bitmap: Bitmap, maxEdgePx: Int): Bitmap {
        val edge = max(bitmap.width, bitmap.height)
        if (edge <= maxEdgePx) return bitmap
        val scale = maxEdgePx.toFloat() / edge
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun actionPendingIntent(
        context: Context,
        appWidgetId: Int,
        action: String,
    ): PendingIntent {
        val intent = Intent(context, PhotoWidgetProvider::class.java).apply {
            this.action = action
            putExtra(EXTRA_APP_WIDGET_ID, appWidgetId)
        }
        val actionCode = when (action) {
            ACTION_PREVIOUS -> 1
            ACTION_REFRESH -> 2
            ACTION_NEXT -> 3
            ACTION_SHOW_CONTROLS -> 4
            else -> 5
        }
        return PendingIntent.getBroadcast(
            context,
            appWidgetId * REQUEST_CODE_STRIDE + actionCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId * REQUEST_CODE_STRIDE + OPEN_APP_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun widgetComponent(context: Context): ComponentName =
        ComponentName(context, PhotoWidgetProvider::class.java)

    companion object {
        const val ACTION_NEXT = "com.miniphoto.viewer.widget.action.NEXT"
        const val ACTION_PREVIOUS = "com.miniphoto.viewer.widget.action.PREVIOUS"
        const val ACTION_REFRESH = "com.miniphoto.viewer.widget.action.REFRESH"
        const val ACTION_SHOW_CONTROLS = "com.miniphoto.viewer.widget.action.SHOW_CONTROLS"
        const val ACTION_HIDE_CONTROLS = "com.miniphoto.viewer.widget.action.HIDE_CONTROLS"
        const val EXTRA_APP_WIDGET_ID = "com.miniphoto.viewer.widget.extra.APP_WIDGET_ID"

        private const val WIDGET_PHOTO_LIMIT = 500
        private const val MAX_BITMAP_EDGE_PX = 512
        private const val MIN_BITMAP_EDGE_PX = 128
        private const val DEFAULT_WIDGET_DP = 110
        private const val REQUEST_CODE_STRIDE = 10
        private const val OPEN_APP_CODE = 6
    }
}
