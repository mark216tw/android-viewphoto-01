package com.miniphoto.viewer.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.miniphoto.viewer.DisplayMode
import com.miniphoto.viewer.ExternalOpenMode
import com.miniphoto.viewer.ExternalPhotoRequest
import com.miniphoto.viewer.GalleryUiState
import com.miniphoto.viewer.data.Photo
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface Screen {
    data object Gallery : Screen
    data object Settings : Screen
    data class Viewer(
        val initialPhotoId: Long,
        val bucketName: String?,
        val viewport: PhotoViewport = PhotoViewport(),
    ) : Screen
    data class ExternalViewer(
        val photo: Photo,
        val viewport: PhotoViewport = PhotoViewport(),
    ) : Screen
    data class Editor(
        val photo: Photo,
        val viewport: PhotoViewport,
        val bucketName: String?,
        val returnToExternal: Boolean = false,
        val directExternalEdit: Boolean = false,
    ) : Screen
}

private enum class GallerySection { PHOTOS, FOLDERS }

@Composable
fun GalleryApp(
    state: GalleryUiState,
    hasPermission: Boolean,
    requestPermission: () -> Unit,
    refresh: () -> Unit,
    share: (Uri) -> Unit,
    delete: (Uri) -> Unit,
    externalPhotoRequest: ExternalPhotoRequest?,
    clearExternalPhoto: () -> Unit,
    displayMode: DisplayMode,
    setDisplayMode: (DisplayMode) -> Unit,
    setPhotoViewerVisible: (Boolean) -> Unit,
) {
    var screen: Screen by remember { mutableStateOf(Screen.Gallery) }

    LaunchedEffect(screen) {
        setPhotoViewerVisible(screen is Screen.Viewer || screen is Screen.ExternalViewer)
    }

    LaunchedEffect(externalPhotoRequest) {
        externalPhotoRequest?.let { request ->
            screen = when (request.mode) {
                ExternalOpenMode.VIEW -> Screen.ExternalViewer(request.photo)
                ExternalOpenMode.EDIT -> Screen.Editor(
                    photo = request.photo,
                    viewport = PhotoViewport(),
                    bucketName = null,
                    directExternalEdit = true,
                )
            }
        }
    }

    BackHandler(screen != Screen.Gallery) {
        screen = when (val current = screen) {
            is Screen.Editor -> when {
                current.directExternalEdit -> {
                    clearExternalPhoto()
                    Screen.Gallery
                }
                current.returnToExternal -> Screen.ExternalViewer(current.photo, current.viewport)
                else -> Screen.Viewer(current.photo.id, current.bucketName, current.viewport)
            }
            is Screen.ExternalViewer -> {
                clearExternalPhoto()
                Screen.Gallery
            }
            else -> Screen.Gallery
        }
    }
    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen",
    ) { target ->
        when (target) {
            Screen.Gallery -> GalleryScreen(
                state = state,
                hasPermission = hasPermission,
                requestPermission = requestPermission,
                refresh = refresh,
                openPhoto = { photo, bucketName ->
                    screen = Screen.Viewer(photo.id, bucketName)
                },
                openSettings = { screen = Screen.Settings },
            )
            Screen.Settings -> SettingsScreen(
                displayMode = displayMode,
                setDisplayMode = setDisplayMode,
                close = { screen = Screen.Gallery },
            )
            is Screen.Viewer -> {
                val viewerPhotos = remember(state.photos, target.bucketName) {
                    state.photos.filter { target.bucketName == null || it.bucketName == target.bucketName }
                }
                val initialIndex = viewerPhotos.indexOfFirst { it.id == target.initialPhotoId }
                    .coerceAtLeast(0)
                PhotoViewer(
                    photos = viewerPhotos,
                    initialIndex = initialIndex,
                    close = { screen = Screen.Gallery },
                    share = share,
                    delete = { photo -> delete(photo.uri) },
                    initialViewport = target.viewport,
                    edit = { photo, viewport ->
                        screen = Screen.Editor(photo, viewport, target.bucketName)
                    },
                )
            }
            is Screen.ExternalViewer -> PhotoViewer(
                photos = listOf(target.photo),
                initialIndex = 0,
                close = {
                    clearExternalPhoto()
                    screen = Screen.Gallery
                },
                share = share,
                delete = null,
                initialViewport = target.viewport,
                edit = { photo, viewport ->
                    screen = Screen.Editor(
                        photo = photo,
                        viewport = viewport,
                        bucketName = null,
                        returnToExternal = true,
                    )
                },
            )
            is Screen.Editor -> PhotoEditorScreen(
                photo = target.photo,
                initialViewport = target.viewport,
                close = {
                    screen = when {
                        target.directExternalEdit -> {
                            clearExternalPhoto()
                            Screen.Gallery
                        }
                        target.returnToExternal -> Screen.ExternalViewer(target.photo, target.viewport)
                        else -> Screen.Viewer(target.photo.id, target.bucketName, target.viewport)
                    }
                },
                saved = {
                    refresh()
                    if (target.returnToExternal || target.directExternalEdit) clearExternalPhoto()
                    screen = Screen.Gallery
                },
                share = share,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun GalleryScreen(
    state: GalleryUiState,
    hasPermission: Boolean,
    requestPermission: () -> Unit,
    refresh: () -> Unit,
    openPhoto: (Photo, String?) -> Unit,
    openSettings: () -> Unit,
) {
    var section by remember { mutableStateOf(GallerySection.PHOTOS) }
    var selectedBucket by remember { mutableStateOf<String?>(null) }
    val visiblePhotos = remember(state.photos, selectedBucket) {
        state.photos.filter { selectedBucket == null || it.bucketName == selectedBucket }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            selectedBucket ?: if (section == GallerySection.FOLDERS) "資料夾" else "mini相片瀏覽器",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (state.photos.isNotEmpty()) {
                            Text(
                                if (section == GallerySection.FOLDERS && selectedBucket == null) {
                                    "${state.photos.map { it.bucketName }.distinct().size} 個資料夾"
                                } else {
                                    "${visiblePhotos.size} 張圖片"
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (selectedBucket != null) {
                        IconButton(onClick = {
                            selectedBucket = null
                            section = GallerySection.FOLDERS
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回資料夾")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = refresh, enabled = hasPermission) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "重新整理")
                    }
                    IconButton(onClick = openSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "設定")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = section == GallerySection.PHOTOS,
                    onClick = {
                        section = GallerySection.PHOTOS
                        selectedBucket = null
                    },
                    icon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                    label = { Text("相片") },
                )
                NavigationBarItem(
                    selected = section == GallerySection.FOLDERS,
                    onClick = {
                        section = GallerySection.FOLDERS
                        selectedBucket = null
                    },
                    icon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                    label = { Text("資料夾") },
                )
            }
        },
    ) { padding ->
        when {
            !hasPermission -> PermissionEmptyState(requestPermission, Modifier.padding(padding))
            state.loading && state.photos.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            state.error != null -> MessageState(
                title = "無法讀取圖片",
                message = state.error,
                action = "再試一次",
                onClick = refresh,
                modifier = Modifier.padding(padding),
            )
            state.photos.isEmpty() -> MessageState(
                title = "還沒有圖片",
                message = "拍攝或下載圖片後，會顯示在這裡。",
                action = "重新整理",
                onClick = refresh,
                modifier = Modifier.padding(padding),
            )
            else -> {
                if (section == GallerySection.FOLDERS && selectedBucket == null) {
                    FolderGrid(
                        photos = state.photos,
                        modifier = Modifier.padding(padding),
                        openFolder = {
                            selectedBucket = it
                        },
                    )
                } else {
                    PhotoGrid(
                        photos = visiblePhotos,
                        modifier = Modifier.padding(padding),
                        openPhoto = { photo -> openPhoto(photo, selectedBucket) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    displayMode: DisplayMode,
    setDisplayMode: (DisplayMode) -> Unit,
    close: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Text(
                text = "顯示模式",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp),
            )
            DisplayModeItem(
                title = "系統",
                description = "跟隨裝置設定",
                icon = Icons.Rounded.BrightnessAuto,
                selected = displayMode == DisplayMode.SYSTEM,
                onClick = { setDisplayMode(DisplayMode.SYSTEM) },
            )
            DisplayModeItem(
                title = "淺色",
                description = "固定使用淺色模式",
                icon = Icons.Rounded.LightMode,
                selected = displayMode == DisplayMode.LIGHT,
                onClick = { setDisplayMode(DisplayMode.LIGHT) },
            )
            DisplayModeItem(
                title = "深色",
                description = "固定使用深色模式",
                icon = Icons.Rounded.DarkMode,
                selected = displayMode == DisplayMode.DARK,
                onClick = { setDisplayMode(DisplayMode.DARK) },
            )
        }
    }
}

@Composable
private fun DisplayModeItem(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGrid(
    photos: List<Photo>,
    modifier: Modifier = Modifier,
    openPhoto: (Photo) -> Unit,
) {
    val grouped = remember(photos) { photos.groupBy { photoDateLabel(it.dateTakenMillis) } }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        state = rememberLazyGridState(),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        grouped.forEach { (date, datedPhotos) ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "date-$date") {
                Text(
                    text = date,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 10.dp, top = 20.dp, bottom = 8.dp),
                )
            }
            items(datedPhotos, key = { it.id }) { photo ->
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(124.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { openPhoto(photo) },
                )
            }
        }
    }
}

@Composable
private fun FolderGrid(
    photos: List<Photo>,
    modifier: Modifier = Modifier,
    openFolder: (String) -> Unit,
) {
    val folders = remember(photos) { photos.groupBy { it.bucketName }.toList().sortedBy { it.first } }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(folders, key = { it.first }) { (name, folderPhotos) ->
            Column(Modifier.clickable { openFolder(name) }) {
                AsyncImage(
                    model = folderPhotos.first().uri,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(142.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Text("${folderPhotos.size} 張", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PermissionEmptyState(onClick: () -> Unit, modifier: Modifier = Modifier) {
    MessageState(
        title = "允許查看圖片",
        message = "mini相片瀏覽器只會在裝置上讀取你授權的圖片，不會上傳內容。",
        action = "選擇可查看的圖片",
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun MessageState(
    title: String,
    message: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.Image,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClick) { Text(action) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PhotoViewer(
    photos: List<Photo>,
    initialIndex: Int,
    close: () -> Unit,
    share: (Uri) -> Unit,
    delete: ((Photo) -> Unit)?,
    initialViewport: PhotoViewport,
    edit: (Photo, PhotoViewport) -> Unit,
) {
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { close() }
        return
    }
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
    var showInfo by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    val viewports = remember { mutableStateMapOf<Long, PhotoViewport>() }
    LaunchedEffect(initialIndex, initialViewport) {
        photos.getOrNull(initialIndex)?.let { viewports[it.id] = initialViewport }
    }
    val currentPage = pagerState.currentPage.coerceIn(0, photos.lastIndex)
    val currentPhoto = photos[currentPage]

    LaunchedEffect(photos.size) {
        val validPage = pagerState.currentPage.coerceIn(0, photos.lastIndex)
        if (validPage != pagerState.currentPage) pagerState.scrollToPage(validPage)
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            if (controlsVisible) {
                TopAppBar(
                    title = {
                        Text(
                            "${currentPage + 1} / ${photos.size}",
                            color = Color.White,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = close) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showInfo = true }) {
                            Icon(Icons.Rounded.Info, "圖片資訊", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = .72f)),
                )
            }
        },
        bottomBar = {
            if (controlsVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = .76f))
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    ViewerAction(Icons.Rounded.Share, "分享") { share(currentPhoto.uri) }
                    ViewerAction(Icons.Rounded.Edit, "編輯") {
                        edit(currentPhoto, viewports[currentPhoto.id] ?: PhotoViewport())
                    }
                    delete?.let { deletePhoto ->
                        ViewerAction(Icons.Rounded.Delete, "刪除") { deletePhoto(currentPhoto) }
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { page ->
            val photo = photos[page]
            ZoomablePhoto(
                photo = photo,
                viewport = viewports[photo.id] ?: PhotoViewport(),
                onViewportChange = { viewports[photo.id] = it },
                onTap = { controlsVisible = !controlsVisible },
            )
        }
    }

    if (showInfo) {
        PhotoInfoSheet(currentPhoto) { showInfo = false }
    }
}

@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, action: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = action) { Icon(icon, label, tint = Color.White) }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoInfoSheet(photo: Photo, dismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text("圖片資訊", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            InfoRow("檔案名稱", photo.name)
            InfoRow("解析度", "${photo.width} × ${photo.height}")
            InfoRow("檔案大小", formatFileSize(photo.size))
            InfoRow("資料夾", photo.bucketName)
            InfoRow("日期", DateFormat.getDateTimeInstance().format(Date(photo.dateTakenMillis)))
            InfoRow("格式", photo.mimeType)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, modifier = Modifier.weight(.35f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(.65f))
    }
    HorizontalDivider()
}

private fun photoDateLabel(time: Long): String = SimpleDateFormat("yyyy年 M月 d日 EEEE", Locale.TAIWAN)
    .format(Date(time))

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.TAIWAN, "%.1f MB", bytes / 1024f / 1024f)
    bytes >= 1024 -> String.format(Locale.TAIWAN, "%.1f KB", bytes / 1024f)
    else -> "$bytes B"
}
