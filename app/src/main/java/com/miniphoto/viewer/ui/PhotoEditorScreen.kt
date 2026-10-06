package com.miniphoto.viewer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.miniphoto.viewer.data.Photo
import com.miniphoto.viewer.data.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class ExportAction { SAVE, SHARE }

private const val EDITOR_PREFERENCES = "photo_editor_settings"
private const val PEN_SIZE_KEY = "pen_size"
private const val MARKER_SIZE_KEY = "marker_size"
private const val ERASER_SIZE_KEY = "eraser_size"
private const val BLUR_SIZE_KEY = "blur_size"
private const val PEN_COLOR_KEY = "pen_color"
private const val MARKER_COLOR_KEY = "marker_color"
private const val DEFAULT_EDIT_COLOR = -2_536_907

private enum class CropDrag { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE }

private class StrokePathCache {
    private var bounds = Rect.Zero
    private val paths = java.util.IdentityHashMap<EditStroke, Path>()

    fun path(stroke: EditStroke, currentBounds: Rect): Path {
        if (bounds != currentBounds) {
            bounds = currentBounds
            paths.clear()
        }
        return paths.getOrPut(stroke) { stroke.toPath(currentBounds) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    photo: Photo,
    initialViewport: PhotoViewport = PhotoViewport(),
    close: () -> Unit,
    saved: (android.net.Uri) -> Unit,
    share: (android.net.Uri) -> Unit,
    writePermissionGranted: Boolean = true,
    requestWritePermission: () -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val editorViewModel: PhotoEditorViewModel = viewModel(key = "photo-editor-${photo.id}")
    editorViewModel.bind(photo.id, initialViewport)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val editorPreferences = remember(context) {
        context.getSharedPreferences(EDITOR_PREFERENCES, Context.MODE_PRIVATE)
    }
    var bitmap by remember(photo.id) { mutableStateOf<Bitmap?>(null) }
    var blurSource by remember(photo.id) { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val strokes = editorViewModel.strokes
    val redoStrokes = editorViewModel.redoStrokes
    val tool = editorViewModel.tool
    var penSize by remember {
        mutableFloatStateOf(editorPreferences.getFloat(PEN_SIZE_KEY, .5f).coerceIn(0f, 1f))
    }
    var markerSize by remember {
        mutableFloatStateOf(editorPreferences.getFloat(MARKER_SIZE_KEY, .48f).coerceIn(0f, 1f))
    }
    var eraserSize by remember {
        mutableFloatStateOf(editorPreferences.getFloat(ERASER_SIZE_KEY, .12f).coerceIn(0f, 1f))
    }
    var blurSize by remember {
        mutableFloatStateOf(editorPreferences.getFloat(BLUR_SIZE_KEY, .25f).coerceIn(0f, 1f))
    }
    var penColor by remember {
        mutableStateOf(Color(editorPreferences.getInt(PEN_COLOR_KEY, DEFAULT_EDIT_COLOR)))
    }
    var markerColor by remember {
        mutableStateOf(Color(editorPreferences.getInt(MARKER_COLOR_KEY, DEFAULT_EDIT_COLOR)))
    }
    val selectedColor = if (tool == EditTool.MARKER) markerColor else penColor
    var exportAction by remember { mutableStateOf<ExportAction?>(null) }
    var isTransforming by remember(photo.id) { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var pendingSaveAfterPermission by remember { mutableStateOf(false) }
    val cropMode = editorViewModel.cropMode
    val cropRect = editorViewModel.cropRect
    val currentStroke = editorViewModel.currentStroke
    val editorInitialViewport = editorViewModel.editorInitialViewport
    val isDirty = editorViewModel.isDirty

    LaunchedEffect(photo.uri) {
        runCatching { withContext(Dispatchers.IO) { loadBitmap(context, photo) } }
            .onSuccess { bitmap = it }
            .onFailure { loadError = it.message ?: "無法開啟圖片" }
    }

    LaunchedEffect(bitmap) {
        blurSource = null
        bitmap?.let { source ->
            blurSource = withContext(Dispatchers.Default) { createBlurSource(source) }
        }
    }

    fun flattenAndTransform(transform: (Bitmap) -> Bitmap) {
        val source = bitmap ?: return
        if (isTransforming) return
        val sourceStrokes = strokes.toList()
        val sourceBlur = blurSource
        isTransforming = true
        scope.launch {
            try {
                val transformed = withContext(Dispatchers.Default) {
                    val flattened = renderBitmap(source, sourceStrokes, sourceBlur)
                    try {
                        val result = transform(flattened)
                        if (result !== flattened) flattened.recycle()
                        result
                    } catch (error: Throwable) {
                        flattened.recycle()
                        throw error
                    }
                }
                bitmap = transformed
                blurSource = null
                editorViewModel.resetAfterTransform()
                strokes.clear()
                redoStrokes.clear()
                editorViewModel.markDirty()
            } catch (error: Exception) {
                snackbar.showSnackbar(error.message ?: "無法處理圖片")
            } finally {
                isTransforming = false
            }
        }
    }

    fun transformBitmapAndStrokes(
        transformBitmap: (Bitmap) -> Bitmap,
        transformPoint: (Offset) -> Offset,
    ) {
        val source = bitmap ?: return
        if (isTransforming) return
        val sourceStrokes = strokes.toList()
        val sourceRedoStrokes = redoStrokes.toList()
        isTransforming = true
        scope.launch {
            try {
                val (transformedBitmap, transformedStrokes, transformedRedoStrokes) = withContext(Dispatchers.Default) {
                    Triple(
                        transformBitmap(source),
                        sourceStrokes.map { stroke -> stroke.copy(points = stroke.points.map(transformPoint)) },
                        sourceRedoStrokes.map { stroke -> stroke.copy(points = stroke.points.map(transformPoint)) },
                    )
                }
                bitmap = transformedBitmap
                blurSource = null
                editorViewModel.currentStroke = null
                editorViewModel.resetAfterTransform()
                strokes.clear()
                strokes.addAll(transformedStrokes)
                redoStrokes.clear()
                redoStrokes.addAll(transformedRedoStrokes)
                editorViewModel.markDirty()
            } catch (error: Exception) {
                snackbar.showSnackbar(error.message ?: "無法處理圖片")
            } finally {
                isTransforming = false
            }
        }
    }

    fun export(action: ExportAction) {
        val source = bitmap ?: return
        if (action == ExportAction.SAVE && !writePermissionGranted) {
            pendingSaveAfterPermission = true
            requestWritePermission()
            return
        }
        exportAction = action
        scope.launch {
            runCatching {
                val output = withContext(Dispatchers.Default) { renderBitmap(source, strokes.toList(), blurSource) }
                try {
                    val repository = PhotoRepository(context)
                    if (action == ExportAction.SHARE) repository.saveShareBitmap(output) else repository.saveBitmap(output)
                } finally {
                    output.recycle()
                }
            }.onSuccess { outputUri ->
                exportAction = null
                if (action == ExportAction.SAVE) editorViewModel.markSaved()
                if (action == ExportAction.SHARE) share(outputUri) else saved(outputUri)
            }.onFailure {
                exportAction = null
                snackbar.showSnackbar(it.message ?: "儲存失敗")
            }
        }
    }

    LaunchedEffect(writePermissionGranted) {
        if (writePermissionGranted && pendingSaveAfterPermission) {
            pendingSaveAfterPermission = false
            export(ExportAction.SAVE)
        }
    }

    fun requestClose() {
        if (isTransforming || exportAction != null) return
        if (isDirty) showDiscardDialog = true else close()
    }

    fun handleBack() {
        if (cropMode) editorViewModel.cancelCrop() else requestClose()
    }

    BackHandler(onBack = ::handleBack)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (cropMode) "裁切圖片" else "編輯圖片") },
                navigationIcon = {
                    IconButton(onClick = ::handleBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (cropMode) {
                        TextButton(onClick = {
                            flattenAndTransform { cropBitmap(it, cropRect) }
                            editorViewModel.cropRect = Rect(.08f, .08f, .92f, .92f)
                            editorViewModel.cropMode = false
                        }) {
                            Icon(Icons.Rounded.Check, null)
                            Text("套用")
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (strokes.isNotEmpty()) redoStrokes.add(strokes.removeAt(strokes.lastIndex))
                                editorViewModel.markDirty()
                            },
                            enabled = strokes.isNotEmpty() && !isTransforming,
                        ) { Icon(Icons.AutoMirrored.Rounded.Undo, "復原") }
                        IconButton(
                            onClick = {
                                if (redoStrokes.isNotEmpty()) strokes.add(redoStrokes.removeAt(redoStrokes.lastIndex))
                                editorViewModel.markDirty()
                            },
                            enabled = redoStrokes.isNotEmpty() && !isTransforming,
                        ) { Icon(Icons.AutoMirrored.Rounded.Redo, "重做") }
                        IconButton(
                            onClick = { export(ExportAction.SHARE) },
                            enabled = exportAction == null && bitmap != null && !isTransforming,
                        ) {
                            if (exportAction == ExportAction.SHARE) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Share, "分享")
                            }
                        }
                        IconButton(
                            onClick = { export(ExportAction.SAVE) },
                            enabled = exportAction == null && bitmap != null && !isTransforming,
                        ) {
                            if (exportAction == ExportAction.SAVE) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Rounded.Save, "另存新檔")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!cropMode && bitmap != null) {
                EditorControls(
                    tool = tool,
                    color = selectedColor,
                    size = when (tool) {
                        EditTool.PEN -> penSize
                        EditTool.MARKER -> markerSize
                        EditTool.ERASER -> eraserSize
                        EditTool.BLUR -> blurSize
                    },
                    selectTool = { editorViewModel.tool = it },
                    selectColor = { color ->
                        when (tool) {
                            EditTool.PEN -> {
                                penColor = color
                                editorPreferences.edit().putInt(PEN_COLOR_KEY, color.toArgb()).apply()
                            }
                            EditTool.MARKER -> {
                                markerColor = color
                                editorPreferences.edit().putInt(MARKER_COLOR_KEY, color.toArgb()).apply()
                            }
                            else -> Unit
                        }
                    },
                    setSize = {
                        when (tool) {
                            EditTool.PEN -> penSize = it
                            EditTool.MARKER -> markerSize = it
                            EditTool.ERASER -> eraserSize = it
                            EditTool.BLUR -> blurSize = it
                        }
                    },
                    saveSize = {
                        val (key, value) = when (tool) {
                            EditTool.PEN -> PEN_SIZE_KEY to penSize
                            EditTool.MARKER -> MARKER_SIZE_KEY to markerSize
                            EditTool.ERASER -> ERASER_SIZE_KEY to eraserSize
                            EditTool.BLUR -> BLUR_SIZE_KEY to blurSize
                        }
                        editorPreferences.edit().putFloat(key, value.coerceIn(0f, 1f)).apply()
                    },
                    crop = { editorViewModel.cropMode = true },
                    rotate = {
                        transformBitmapAndStrokes(
                            transformBitmap = ::rotateBitmap,
                            transformPoint = { point -> Offset(1f - point.y, point.x) },
                        )
                    },
                    flip = {
                        transformBitmapAndStrokes(
                            transformBitmap = ::flipBitmap,
                            transformPoint = { point -> Offset(1f - point.x, point.y) },
                        )
                    },
                    enabled = !isTransforming && exportAction == null,
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFF171A18)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                loadError != null -> Text(loadError!!, color = Color.White)
                bitmap == null -> CircularProgressIndicator(color = Color.White)
                else -> EditorCanvas(
                    bitmap = bitmap!!,
                    initialViewport = editorInitialViewport,
                    blurSource = blurSource,
                    strokes = strokes,
                    currentStroke = currentStroke,
                    interactionEnabled = !isTransforming && exportAction == null,
                    tool = tool,
                    color = selectedColor,
                    widthPxAt1x = with(density) {
                        widthDpFor(when (tool) {
                            EditTool.PEN -> penSize
                            EditTool.MARKER -> markerSize
                            EditTool.ERASER -> eraserSize
                            EditTool.BLUR -> blurSize
                        }).dp.toPx()
                    },
                    cropMode = cropMode,
                    cropRect = cropRect,
                    updateCrop = { editorViewModel.cropRect = it },
                    updateStroke = { editorViewModel.currentStroke = it },
                    cancelStroke = { editorViewModel.currentStroke = null },
                    commitStroke = { stroke ->
                        strokes.add(stroke)
                        editorViewModel.currentStroke = null
                        redoStrokes.clear()
                        editorViewModel.markDirty()
                    },
                )
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放棄未儲存的變更？") },
            text = { Text("目前的編輯內容尚未儲存。離開後這些變更會遺失。") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    close()
                }) { Text("放棄變更") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("繼續編輯") }
            },
        )
    }
}

@Composable
private fun EditorControls(
    tool: EditTool,
    color: Color,
    size: Float,
    selectTool: (EditTool) -> Unit,
    selectColor: (Color) -> Unit,
    setSize: (Float) -> Unit,
    saveSize: () -> Unit,
    crop: () -> Unit,
    rotate: () -> Unit,
    flip: () -> Unit,
    enabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolButton("畫筆", Icons.Rounded.Brush, tool == EditTool.PEN, enabled) { selectTool(EditTool.PEN) }
            ToolButton("螢光筆", Icons.Rounded.Edit, tool == EditTool.MARKER, enabled) { selectTool(EditTool.MARKER) }
            ToolButton("橡皮擦", Icons.Rounded.DeleteSweep, tool == EditTool.ERASER, enabled) { selectTool(EditTool.ERASER) }
            ToolButton("模糊", Icons.Rounded.BlurOn, tool == EditTool.BLUR, enabled) { selectTool(EditTool.BLUR) }
            ToolButton("裁切", Icons.Rounded.ContentCut, false, enabled, crop)
            ToolButton("旋轉", Icons.AutoMirrored.Rounded.RotateRight, false, enabled, rotate)
            ToolButton("水平翻轉", Icons.Rounded.Flip, false, enabled, flip)
        }
        if (tool == EditTool.PEN || tool == EditTool.MARKER) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                editColors.forEach { item ->
                    Box(
                        Modifier
                            .size(if (item == color) 28.dp else 24.dp)
                            .background(item, CircleShape)
                            .then(if (item == color) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable(enabled = enabled) { selectColor(item) }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (tool == EditTool.BLUR) "範圍" else "粗細", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = size,
                onValueChange = { if (enabled) setSize(it) },
                onValueChangeFinished = { if (enabled) saveSize() },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            WidthPreview(
                tool = tool,
                color = color,
                size = size,
            )
        }
    }
}

@Composable
private fun WidthPreview(tool: EditTool, color: Color, size: Float) {
    val previewColor = when (tool) {
        EditTool.PEN -> color
        EditTool.MARKER -> color.copy(alpha = .38f)
        EditTool.ERASER -> MaterialTheme.colorScheme.onSurfaceVariant
        EditTool.BLUR -> MaterialTheme.colorScheme.onSurface.copy(alpha = .25f)
    }
    Canvas(Modifier.padding(start = 8.dp).size(34.dp)) {
        val radius = widthDpFor(size).dp.toPx() / 2f
        if (tool == EditTool.ERASER) {
            drawCircle(previewColor, radius, style = Stroke(width = 2.dp.toPx()))
        } else {
            drawCircle(previewColor, radius)
        }
    }
}

private fun widthDpFor(size: Float): Float = 5f + 27f * size.coerceIn(0f, 1f)

@Composable
private fun ToolButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                MaterialTheme.shapes.medium,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private val editColors = listOf(
    Color(0xFFD94A35), Color(0xFFF2B705), Color(0xFF2F9E63),
    Color(0xFF2878C8), Color(0xFF7D50B5), Color(0xFFFF8A00),
    Color(0xFFFF4FA3), Color(0xFF00B8D9), Color.White, Color.Black,
)

@Composable
private fun EditorCanvas(
    bitmap: Bitmap,
    blurSource: Bitmap?,
    initialViewport: PhotoViewport,
    strokes: List<EditStroke>,
    currentStroke: EditStroke?,
    interactionEnabled: Boolean,
    tool: EditTool,
    color: Color,
    widthPxAt1x: Float,
    cropMode: Boolean,
    cropRect: Rect,
    updateCrop: (Rect) -> Unit,
    updateStroke: (EditStroke) -> Unit,
    cancelStroke: () -> Unit,
    commitStroke: (EditStroke) -> Unit,
) {
    var imageBounds by remember(bitmap) { mutableStateOf(Rect.Zero) }
    var viewportSize by remember(bitmap) { mutableStateOf(Size.Zero) }
    var fittedImageSize by remember(bitmap) { mutableStateOf(Size.Zero) }
    var zoomScale by remember(bitmap) { mutableFloatStateOf(1f) }
    var panOffset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var viewportInitialized by remember(bitmap, initialViewport) { mutableStateOf(false) }
    val currentCropRect by rememberUpdatedState(cropRect)
    val strokePathCache = remember(bitmap) { StrokePathCache() }

    LaunchedEffect(viewportSize, fittedImageSize, initialViewport, cropMode) {
        if (cropMode) {
            zoomScale = 1f
            panOffset = Offset.Zero
            viewportInitialized = true
        } else if (!viewportInitialized && viewportSize != Size.Zero && fittedImageSize != Size.Zero) {
            val scale = initialViewport.scale.coerceIn(1f, 20f)
            zoomScale = scale
            panOffset = initialViewport.toOffset(scale, fittedImageSize, viewportSize)
            viewportInitialized = true
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(bitmap, cropMode, tool, color, widthPxAt1x, interactionEnabled) {
                if (!interactionEnabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var activeStroke: EditStroke? = null
                    var cropDrag: CropDrag? = null
                    var cropStartRect = Rect.Zero
                    var cropStartPoint = Offset.Zero
                    var transforming = false

                    if (imageBounds.contains(down.position)) {
                        val point = down.position.toNormalized(imageBounds)
                        if (cropMode) {
                            cropStartRect = currentCropRect
                            cropStartPoint = point
                            cropDrag = findCropDrag(
                                position = down.position,
                                bounds = imageBounds,
                                crop = currentCropRect,
                                handleRadius = 32.dp.toPx(),
                            )
                        } else {
                            val fittedShortSide = min(fittedImageSize.width, fittedImageSize.height)
                            if (fittedShortSide <= 0f) return@awaitEachGesture
                            val normalizedWidth = widthPxAt1x / fittedShortSide
                            EditStroke(mutableListOf(point), color, normalizedWidth, tool).let {
                                activeStroke = it
                                updateStroke(it)
                            }
                        }
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            if (!transforming) {
                                transforming = true
                                activeStroke = null
                                cropDrag = null
                                cancelStroke()
                            }
                            val oldScale = zoomScale
                            val nextScale = (oldScale * event.calculateZoom()).coerceIn(1f, 20f)
                            if (nextScale == 1f) {
                                panOffset = Offset.Zero
                            } else if (viewportSize != Size.Zero && fittedImageSize != Size.Zero) {
                                val appliedZoom = nextScale / oldScale
                                val viewportCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                                val centroidOffset = event.calculateCentroid() - viewportCenter
                                val candidate = panOffset * appliedZoom +
                                    centroidOffset * (1f - appliedZoom) + event.calculatePan()
                                val maxX = max(0f, (fittedImageSize.width * nextScale - viewportSize.width) / 2f)
                                val maxY = max(0f, (fittedImageSize.height * nextScale - viewportSize.height) / 2f)
                                panOffset = Offset(
                                    candidate.x.coerceIn(-maxX, maxX),
                                    candidate.y.coerceIn(-maxY, maxY),
                                )
                            }
                            zoomScale = nextScale
                            event.changes.forEach { it.consume() }
                        } else if (pressed.size == 1 && !transforming) {
                            val change = pressed.first()
                            if (cropMode || imageBounds.contains(change.position)) {
                                val point = change.position.toNormalized(imageBounds)
                                if (cropMode) {
                                    cropDrag?.let { activeDrag ->
                                        val updated = if (activeDrag == CropDrag.MOVE) {
                                            moveCropRect(cropStartRect, point - cropStartPoint)
                                        } else {
                                            resizeCropRect(cropStartRect, activeDrag, point)
                                        }
                                        updateCrop(updated)
                                    }
                                } else {
                                    activeStroke?.let { stroke ->
                                        val points = stroke.points as MutableList<Offset>
                                        val minimumDistance = max(.0008f, stroke.width * .04f)
                                        if ((point - points.last()).getDistanceSquared() >= minimumDistance * minimumDistance) {
                                            points.add(point)
                                            stroke.copy(revision = stroke.revision + 1).let {
                                                activeStroke = it
                                                updateStroke(it)
                                            }
                                        }
                                    }
                                }
                                change.consume()
                            }
                        }

                        if (pressed.isEmpty()) {
                            if (!transforming && !cropMode) {
                                activeStroke?.let { commitStroke(it.copy(points = it.points.toList())) }
                            }
                            break
                        }
                    }
                }
            },
    ) {
        val fitScale = min(size.width / bitmap.width, size.height / bitmap.height)
        val widthPx = bitmap.width * fitScale
        val heightPx = bitmap.height * fitScale
        val scaledWidth = widthPx * zoomScale
        val scaledHeight = heightPx * zoomScale
        val left = (size.width - scaledWidth) / 2f + panOffset.x
        val top = (size.height - scaledHeight) / 2f + panOffset.y
        val bounds = Rect(left, top, left + scaledWidth, top + scaledHeight)
        imageBounds = bounds
        viewportSize = size
        fittedImageSize = Size(widthPx, heightPx)

        drawImage(
            image = bitmap.asImageBitmap(),
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(scaledWidth.roundToInt(), scaledHeight.roundToInt()),
        )

        val hasBlurStrokes = strokes.any { it.tool == EditTool.BLUR } || currentStroke?.tool == EditTool.BLUR
        if (blurSource != null && hasBlurStrokes) {
            drawContext.canvas.saveLayer(bounds, androidx.compose.ui.graphics.Paint())
            clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
                strokes.forEach { stroke ->
                    if (stroke.tool == EditTool.BLUR) {
                        drawBlurMask(stroke, bounds, strokePathCache.path(stroke, bounds))
                    }
                }
                currentStroke?.takeIf { it.tool == EditTool.BLUR }?.let { drawBlurMask(it, bounds) }
                drawImage(
                    image = blurSource.asImageBitmap(),
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(scaledWidth.roundToInt(), scaledHeight.roundToInt()),
                    blendMode = BlendMode.SrcIn,
                    filterQuality = FilterQuality.Medium,
                )
            }
            drawContext.canvas.restore()
        }

        val hasAnnotationStrokes = strokes.any { it.tool != EditTool.BLUR } ||
            currentStroke?.tool?.let { it != EditTool.BLUR } == true
        if (hasAnnotationStrokes) {
            drawContext.canvas.saveLayer(bounds, androidx.compose.ui.graphics.Paint())
            clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
                strokes.forEach { stroke ->
                    if (stroke.tool != EditTool.BLUR) {
                        drawEditStroke(stroke, bounds, strokePathCache.path(stroke, bounds))
                    }
                }
                currentStroke?.takeIf { it.tool != EditTool.BLUR }?.let { drawEditStroke(it, bounds) }
            }
            drawContext.canvas.restore()
        }
        if (cropMode) drawCropOverlay(bounds, cropRect)
    }
}

private fun EditStroke.toPath(bounds: Rect): Path = Path().apply {
    if (points.isEmpty()) return@apply
    val first = points.first().fromNormalized(bounds)
    moveTo(first.x, first.y)
    points.drop(1).forEach {
        val point = it.fromNormalized(bounds)
        lineTo(point.x, point.y)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEditStroke(
    stroke: EditStroke,
    bounds: Rect,
    cachedPath: Path? = null,
) {
    if (stroke.points.isEmpty()) return
    if (stroke.points.size == 1) {
        drawCircle(
            color = if (stroke.tool == EditTool.ERASER) Color.Transparent else stroke.color,
            radius = stroke.width * min(bounds.width, bounds.height) / 2f,
            center = stroke.points.first().fromNormalized(bounds),
            alpha = if (stroke.tool == EditTool.MARKER) .38f else 1f,
            blendMode = if (stroke.tool == EditTool.ERASER) BlendMode.Clear else BlendMode.SrcOver,
        )
        return
    }
    drawPath(
        path = cachedPath ?: stroke.toPath(bounds),
        color = if (stroke.tool == EditTool.ERASER) Color.Transparent else stroke.color,
        alpha = if (stroke.tool == EditTool.MARKER) .38f else 1f,
        style = Stroke(width = stroke.width * min(bounds.width, bounds.height), cap = StrokeCap.Round),
        blendMode = if (stroke.tool == EditTool.ERASER) BlendMode.Clear else BlendMode.SrcOver,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlurMask(
    stroke: EditStroke,
    bounds: Rect,
    cachedPath: Path? = null,
) {
    if (stroke.points.isEmpty()) return
    val strokeWidth = stroke.width * min(bounds.width, bounds.height)
    if (stroke.points.size == 1) {
        drawCircle(
            color = Color.White,
            radius = strokeWidth / 2f,
            center = stroke.points.first().fromNormalized(bounds),
        )
        return
    }
    drawPath(
        path = cachedPath ?: stroke.toPath(bounds),
        color = Color.White,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCropOverlay(bounds: Rect, crop: Rect) {
    val selected = Rect(
        bounds.left + crop.left * bounds.width,
        bounds.top + crop.top * bounds.height,
        bounds.left + crop.right * bounds.width,
        bounds.top + crop.bottom * bounds.height,
    )
    val shade = Color.Black.copy(alpha = .58f)
    drawRect(shade, Offset(bounds.left, bounds.top), androidx.compose.ui.geometry.Size(bounds.width, selected.top - bounds.top))
    drawRect(shade, Offset(bounds.left, selected.bottom), androidx.compose.ui.geometry.Size(bounds.width, bounds.bottom - selected.bottom))
    drawRect(shade, Offset(bounds.left, selected.top), androidx.compose.ui.geometry.Size(selected.left - bounds.left, selected.height))
    drawRect(shade, Offset(selected.right, selected.top), androidx.compose.ui.geometry.Size(bounds.right - selected.right, selected.height))
    drawRect(Color.White, selected.topLeft, selected.size, style = Stroke(3.dp.toPx()))
    val handle = 18.dp.toPx()
    listOf(selected.topLeft, selected.topRight, selected.bottomLeft, selected.bottomRight).forEach {
        drawCircle(Color(0xFFF4C95D), handle / 2f, it)
    }
}

private fun Offset.toNormalized(bounds: Rect) = Offset(
    ((x - bounds.left) / bounds.width).coerceIn(0f, 1f),
    ((y - bounds.top) / bounds.height).coerceIn(0f, 1f),
)

private fun Offset.fromNormalized(bounds: Rect) = Offset(
    bounds.left + x * bounds.width,
    bounds.top + y * bounds.height,
)

private fun findCropDrag(
    position: Offset,
    bounds: Rect,
    crop: Rect,
    handleRadius: Float,
): CropDrag? {
    val selected = Rect(
        crop.topLeft.fromNormalized(bounds),
        crop.bottomRight.fromNormalized(bounds),
    )
    val distances = listOf(
        CropDrag.TOP_LEFT to (position - selected.topLeft).getDistanceSquared(),
        CropDrag.TOP_RIGHT to (position - selected.topRight).getDistanceSquared(),
        CropDrag.BOTTOM_LEFT to (position - selected.bottomLeft).getDistanceSquared(),
        CropDrag.BOTTOM_RIGHT to (position - selected.bottomRight).getDistanceSquared(),
    )
    val nearest = distances.minBy { it.second }
    return when {
        nearest.second <= handleRadius * handleRadius -> nearest.first
        selected.contains(position) -> CropDrag.MOVE
        else -> null
    }
}

private fun resizeCropRect(rect: Rect, drag: CropDrag, point: Offset): Rect {
    val minimum = .08f
    return when (drag) {
        CropDrag.TOP_LEFT -> Rect(
            point.x.coerceAtMost(rect.right - minimum),
            point.y.coerceAtMost(rect.bottom - minimum),
            rect.right,
            rect.bottom,
        )
        CropDrag.TOP_RIGHT -> Rect(
            rect.left,
            point.y.coerceAtMost(rect.bottom - minimum),
            point.x.coerceAtLeast(rect.left + minimum),
            rect.bottom,
        )
        CropDrag.BOTTOM_LEFT -> Rect(
            point.x.coerceAtMost(rect.right - minimum),
            rect.top,
            rect.right,
            point.y.coerceAtLeast(rect.top + minimum),
        )
        CropDrag.BOTTOM_RIGHT -> Rect(
            rect.left,
            rect.top,
            point.x.coerceAtLeast(rect.left + minimum),
            point.y.coerceAtLeast(rect.top + minimum),
        )
        CropDrag.MOVE -> rect
    }
}

private fun moveCropRect(rect: Rect, delta: Offset): Rect {
    val horizontal = delta.x.coerceIn(-rect.left, 1f - rect.right)
    val vertical = delta.y.coerceIn(-rect.top, 1f - rect.bottom)
    return rect.translate(Offset(horizontal, vertical))
}

private fun loadBitmap(context: Context, photo: Photo): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, photo.uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val maxSide = max(info.size.width, info.size.height)
            if (maxSide > 4096) decoder.setTargetSampleSize(ceil(maxSide / 4096f).toInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val orientation = runCatching {
            context.contentResolver.openInputStream(photo.uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(photo.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
        val decoded = context.contentResolver.openInputStream(photo.uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("無法解碼圖片")
        applyExifOrientation(decoded, orientation)
    }
}

private fun applyExifOrientation(source: Bitmap, orientation: Int): Bitmap {
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
        else -> return source
    }
    return try {
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also {
            if (it !== source) source.recycle()
        }
    } catch (error: Throwable) {
        source.recycle()
        throw error
    }
}

private fun createBlurSource(source: Bitmap): Bitmap {
    val width = (source.width / 12).coerceAtLeast(1)
    val height = (source.height / 12).coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(source, width, height, true)
    return if (scaled === source) source.copy(Bitmap.Config.ARGB_8888, false) else scaled
}

private fun renderBitmap(source: Bitmap, strokes: List<EditStroke>, preparedBlurSource: Bitmap?): Bitmap {
    val base = source.copy(Bitmap.Config.ARGB_8888, true)
    if (strokes.isEmpty()) return base
    val blurStrokes = strokes.filter { it.tool == EditTool.BLUR }
    if (blurStrokes.isNotEmpty()) {
        val ownsBlur = preparedBlurSource == null
        val blur = preparedBlurSource ?: createBlurSource(source)
        try {
            val shader = BitmapShader(blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply {
                    setScale(source.width / blur.width.toFloat(), source.height / blur.height.toFloat())
                })
            }
            val canvas = android.graphics.Canvas(base)
            blurStrokes.forEach { stroke ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    this.shader = shader
                    strokeWidth = stroke.width * min(base.width, base.height)
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val first = stroke.points.firstOrNull() ?: return@forEach
                if (stroke.points.size == 1) {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(first.x * base.width, first.y * base.height, paint.strokeWidth / 2f, paint)
                } else {
                    val path = android.graphics.Path().apply {
                        moveTo(first.x * base.width, first.y * base.height)
                        stroke.points.drop(1).forEach { lineTo(it.x * base.width, it.y * base.height) }
                    }
                    canvas.drawPath(path, paint)
                }
            }
        } finally {
            if (ownsBlur) blur.recycle()
        }
    }

    val annotationStrokes = strokes.filter { it.tool != EditTool.BLUR }
    if (annotationStrokes.isEmpty()) return base
    val overlay = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = android.graphics.Canvas(overlay)
        annotationStrokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color.toArgb()
                alpha = if (stroke.tool == EditTool.MARKER) 97 else 255
                strokeWidth = stroke.width * min(base.width, base.height)
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                if (stroke.tool == EditTool.ERASER) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            val path = android.graphics.Path().apply {
                moveTo(stroke.points.first().x * base.width, stroke.points.first().y * base.height)
                stroke.points.drop(1).forEach { lineTo(it.x * base.width, it.y * base.height) }
            }
            if (stroke.points.size == 1) {
                val point = stroke.points.first()
                paint.style = Paint.Style.FILL
                canvas.drawCircle(point.x * base.width, point.y * base.height, paint.strokeWidth / 2f, paint)
            } else {
                canvas.drawPath(path, paint)
            }
        }
        android.graphics.Canvas(base).drawBitmap(overlay, 0f, 0f, null)
    } finally {
        overlay.recycle()
    }
    return base
}

private fun rotateBitmap(source: Bitmap): Bitmap = Bitmap.createBitmap(
    source, 0, 0, source.width, source.height, Matrix().apply { postRotate(90f) }, true,
)

private fun flipBitmap(source: Bitmap): Bitmap = Bitmap.createBitmap(
    source, 0, 0, source.width, source.height, Matrix().apply { postScale(-1f, 1f) }, true,
)

private fun cropBitmap(source: Bitmap, crop: Rect): Bitmap {
    val left = (crop.left * source.width).roundToInt().coerceIn(0, source.width - 1)
    val top = (crop.top * source.height).roundToInt().coerceIn(0, source.height - 1)
    val right = (crop.right * source.width).roundToInt().coerceIn(left + 1, source.width)
    val bottom = (crop.bottom * source.height).roundToInt().coerceIn(top + 1, source.height)
    return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
}
