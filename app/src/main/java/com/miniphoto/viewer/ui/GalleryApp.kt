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
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.distinctUntilChanged

private sealed interface Screen {
    data object Gallery : Screen
    data object Settings : Screen
    data class Viewer(
        val initialPhotoId: Long,
        val bucketId: String?,
        val viewport: PhotoViewport = PhotoViewport(),
    ) : Screen
    data class ExternalViewer(
        val photo: Photo,
        val viewport: PhotoViewport = PhotoViewport(),
    ) : Screen
    data class Editor(
        val photo: Photo,
        val viewport: PhotoViewport,
        val bucketId: String?,
        val returnToExternal: Boolean = false,
        val directExternalEdit: Boolean = false,
    ) : Screen
}

private enum class GallerySection { PHOTOS, FOLDERS }

private data class ViewerPosition(
    val photoId: Long,
    val viewport: PhotoViewport,
)

internal data class PendingPhotoDeletion(
    val deletedPhotoId: Long,
    val nextPhotoId: Long?,
    val previousPhotoId: Long?,
    val previousIndex: Int,
)

internal fun resolveViewerIndex(
    photoIds: List<Long>,
    preferredPhotoId: Long,
    initialPhotoId: Long,
    pendingDeletion: PendingPhotoDeletion?,
): Int {
    photoIds.indexOf(preferredPhotoId).takeIf { it >= 0 }?.let { return it }
    if (pendingDeletion?.deletedPhotoId == preferredPhotoId) {
        pendingDeletion.nextPhotoId?.let { id ->
            photoIds.indexOf(id).takeIf { it >= 0 }?.let { return it }
        }
        pendingDeletion.previousPhotoId?.let { id ->
            photoIds.indexOf(id).takeIf { it >= 0 }?.let { return it }
        }
        if (photoIds.isNotEmpty()) return pendingDeletion.previousIndex.coerceIn(photoIds.indices)
    }
    photoIds.indexOf(initialPhotoId).takeIf { it >= 0 }?.let { return it }
    return 0
}

@Composable
fun GalleryApp(
    state: GalleryUiState,
    hasPermission: Boolean,
    requestPermission: () -> Unit,
    refresh: () -> Unit,
    loadMore: () -> Unit,
    share: (Uri) -> Unit,
    delete: (Uri) -> Unit,
    externalPhotoRequest: ExternalPhotoRequest?,
    clearExternalPhoto: () -> Unit,
    displayMode: DisplayMode,
    setDisplayMode: (DisplayMode) -> Unit,
    setPhotoViewerVisible: (Boolean) -> Unit,
    setPhotoViewerControlsVisible: (Boolean) -> Unit,
    finishExternal: () -> Unit,
    externalEditSaved: (Uri) -> Unit,
    writePermissionGranted: Boolean,
    requestWritePermission: () -> Unit,
) {
    var screen: Screen by remember { mutableStateOf(Screen.Gallery) }
    var gallerySection by remember { mutableStateOf(GallerySection.PHOTOS) }
    var selectedBucketId by remember { mutableStateOf<String?>(null) }
    val photoGridState = rememberLazyGridState()
    val folderGridState = rememberLazyGridState()
    var viewerPosition by remember { mutableStateOf<ViewerPosition?>(null) }
    var pendingDeletion by remember { mutableStateOf<PendingPhotoDeletion?>(null) }

    LaunchedEffect(screen) {
        val viewer = screen is Screen.Viewer || screen is Screen.ExternalViewer
        setPhotoViewerVisible(viewer)
        if (!viewer) setPhotoViewerControlsVisible(true)
    }

    LaunchedEffect(externalPhotoRequest) {
        externalPhotoRequest?.let { request ->
            screen = when (request.mode) {
                ExternalOpenMode.VIEW -> Screen.ExternalViewer(request.photo)
                ExternalOpenMode.EDIT -> Screen.Editor(
                    photo = request.photo,
                    viewport = PhotoViewport(),
                    bucketId = null,
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
                    finishExternal()
                    Screen.Gallery
                }
                current.returnToExternal -> Screen.ExternalViewer(current.photo, current.viewport)
                else -> Screen.Viewer(current.photo.id, current.bucketId, current.viewport)
            }
            is Screen.ExternalViewer -> {
                clearExternalPhoto()
                finishExternal()
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
                loadMore = loadMore,
                section = gallerySection,
                selectedBucketId = selectedBucketId,
                photoGridState = photoGridState,
                folderGridState = folderGridState,
                onSectionSelected = { section ->
                    gallerySection = section
                    if (section == GallerySection.PHOTOS) selectedBucketId = null
                },
                onFolderSelected = { bucketId ->
                    gallerySection = GallerySection.FOLDERS
                    selectedBucketId = bucketId
                },
                onFolderBack = {
                    gallerySection = GallerySection.FOLDERS
                    selectedBucketId = null
                },
                openPhoto = { photo, bucketId ->
                    viewerPosition = ViewerPosition(photo.id, PhotoViewport())
                    screen = Screen.Viewer(photo.id, bucketId)
                },
                openSettings = { screen = Screen.Settings },
            )
            Screen.Settings -> SettingsScreen(
                displayMode = displayMode,
                setDisplayMode = setDisplayMode,
                close = { screen = Screen.Gallery },
            )
            is Screen.Viewer -> {
                val viewerPhotos = remember(state.photos, target.bucketId) {
                    state.photos.filter { target.bucketId == null || it.bucketId == target.bucketId }
                }
                val preferredPhotoId = viewerPosition?.photoId ?: target.initialPhotoId
                val initialIndex = resolveViewerIndex(
                    photoIds = viewerPhotos.map { it.id },
                    preferredPhotoId = preferredPhotoId,
                    initialPhotoId = target.initialPhotoId,
                    pendingDeletion = pendingDeletion,
                )
                val resolvedPhotoId = viewerPhotos.getOrNull(initialIndex)?.id
                val resolvedViewport = if (resolvedPhotoId == preferredPhotoId) {
                    viewerPosition?.viewport ?: target.viewport
                } else {
                    PhotoViewport()
                }
                PhotoViewer(
                    photos = viewerPhotos,
                    initialIndex = initialIndex,
                    close = { screen = Screen.Gallery },
                    onPageChanged = { photo, viewport ->
                        viewerPosition = ViewerPosition(photo.id, viewport)
                        if (photo.id != pendingDeletion?.deletedPhotoId) pendingDeletion = null
                    },
                    setControlsVisible = setPhotoViewerControlsVisible,
                    share = share,
                    delete = { photo ->
                        val index = viewerPhotos.indexOfFirst { it.id == photo.id }
                        if (index >= 0) {
                            pendingDeletion = PendingPhotoDeletion(
                                deletedPhotoId = photo.id,
                                nextPhotoId = viewerPhotos.getOrNull(index + 1)?.id,
                                previousPhotoId = viewerPhotos.getOrNull(index - 1)?.id,
                                previousIndex = index,
                            )
                        }
                        delete(photo.uri)
                    },
                    initialViewport = resolvedViewport,
                    hasMore = state.hasMore,
                    loadingMore = state.loadingMore,
                    loadError = state.error,
                    loadMore = loadMore,
                    edit = { photo, viewport ->
                        viewerPosition = ViewerPosition(photo.id, viewport)
                        screen = Screen.Editor(photo, viewport, target.bucketId)
                    },
                )
            }
            is Screen.ExternalViewer -> PhotoViewer(
                photos = listOf(target.photo),
                initialIndex = 0,
                close = {
                    clearExternalPhoto()
                    finishExternal()
                    screen = Screen.Gallery
                },
                onPageChanged = { _, _ -> },
                setControlsVisible = setPhotoViewerControlsVisible,
                share = share,
                delete = null,
                initialViewport = target.viewport,
                hasMore = false,
                loadingMore = false,
                loadError = null,
                loadMore = {},
                edit = { photo, viewport ->
                    screen = Screen.Editor(
                        photo = photo,
                        viewport = viewport,
                        bucketId = null,
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
                            finishExternal()
                            Screen.Gallery
                        }
                        target.returnToExternal -> Screen.ExternalViewer(target.photo, target.viewport)
                        else -> Screen.Viewer(target.photo.id, target.bucketId, target.viewport)
                    }
                },
                saved = { outputUri ->
                    if (target.directExternalEdit) {
                        clearExternalPhoto()
                        externalEditSaved(outputUri)
                    } else {
                        refresh()
                        if (target.returnToExternal) clearExternalPhoto()
                        screen = Screen.Gallery
                    }
                },
                share = share,
                writePermissionGranted = writePermissionGranted,
                requestWritePermission = requestWritePermission,
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
    loadMore: () -> Unit,
    section: GallerySection,
    selectedBucketId: String?,
    photoGridState: LazyGridState,
    folderGridState: LazyGridState,
    onSectionSelected: (GallerySection) -> Unit,
    onFolderSelected: (String) -> Unit,
    onFolderBack: () -> Unit,
    openPhoto: (Photo, String?) -> Unit,
    openSettings: () -> Unit,
) {
    val selectedBucketName = remember(state.photos, selectedBucketId) {
        state.photos.firstOrNull { it.bucketId == selectedBucketId }?.bucketName
    }
    val visiblePhotos = remember(state.photos, selectedBucketId) {
        state.photos.filter { selectedBucketId == null || it.bucketId == selectedBucketId }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            selectedBucketName ?: if (section == GallerySection.FOLDERS) "資料夾" else "mini相片瀏覽器",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (state.photos.isNotEmpty()) {
                            Text(
                                if (section == GallerySection.FOLDERS && selectedBucketId == null) {
                                    "${state.photos.map { it.bucketId }.distinct().size}${if (state.hasMore) "+" else ""} 個資料夾"
                                } else {
                                    "${visiblePhotos.size}${if (state.hasMore) "+" else ""} 張圖片"
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (selectedBucketId != null) {
                        IconButton(onClick = onFolderBack) {
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
                    onClick = { onSectionSelected(GallerySection.PHOTOS) },
                    icon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                    label = { Text("相片") },
                )
                NavigationBarItem(
                    selected = section == GallerySection.FOLDERS,
                    onClick = { onSectionSelected(GallerySection.FOLDERS) },
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
            state.error != null && state.photos.isEmpty() -> MessageState(
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
                if (section == GallerySection.FOLDERS && selectedBucketId == null) {
                    FolderGrid(
                        photos = state.photos,
                        state = folderGridState,
                        hasMore = state.hasMore,
                        loadingMore = state.loadingMore,
                        loadError = state.error,
                        loadMore = loadMore,
                        modifier = Modifier.padding(padding),
                        openFolder = onFolderSelected,
                    )
                } else {
                    PhotoGrid(
                        photos = visiblePhotos,
                        state = photoGridState,
                        hasMore = state.hasMore,
                        loadingMore = state.loadingMore,
                        loadMore = loadMore,
                        loadError = state.error,
                        modifier = Modifier.padding(padding),
                        openPhoto = { photo -> openPhoto(photo, selectedBucketId) },
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
    state: LazyGridState,
    hasMore: Boolean,
    loadingMore: Boolean,
    loadMore: () -> Unit,
    loadError: String?,
    modifier: Modifier = Modifier,
    openPhoto: (Photo) -> Unit,
) {
    val grouped = remember(photos) { photos.groupBy { photoDateLabel(it.dateTakenMillis) } }
    LoadMoreEffect(state, hasMore, loadingMore, loadError, photos.size, loadMore)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        state = state,
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
        if (loadingMore || loadError != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "load-more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loadingMore) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    } else {
                        Button(onClick = loadMore) { Text("載入更多") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderGrid(
    photos: List<Photo>,
    state: LazyGridState,
    hasMore: Boolean,
    loadingMore: Boolean,
    loadError: String?,
    loadMore: () -> Unit,
    modifier: Modifier = Modifier,
    openFolder: (String) -> Unit,
) {
    val folders = remember(photos) {
        photos.groupBy { it.bucketId }
            .toList()
            .sortedBy { it.second.firstOrNull()?.bucketName.orEmpty() }
    }
    LoadMoreEffect(state, hasMore, loadingMore, loadError, folders.size, loadMore)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(folders, key = { it.first }) { (id, folderPhotos) ->
            val name = folderPhotos.firstOrNull()?.bucketName ?: "其他"
            Column(Modifier.clickable { openFolder(id) }) {
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
        if (loadingMore || loadError != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "folder-load-more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loadingMore) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    } else {
                        Button(onClick = loadMore) { Text("載入更多") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadMoreEffect(
    state: LazyGridState,
    hasMore: Boolean,
    loadingMore: Boolean,
    loadError: String?,
    itemCount: Int,
    loadMore: () -> Unit,
) {
    LaunchedEffect(state, hasMore, loadingMore, loadError, itemCount) {
        if (!hasMore || loadingMore || loadError != null) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= state.layoutInfo.totalItemsCount - 12) loadMore()
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
    onPageChanged: (Photo, PhotoViewport) -> Unit,
    setControlsVisible: (Boolean) -> Unit,
    share: (Uri) -> Unit,
    delete: ((Photo) -> Unit)?,
    initialViewport: PhotoViewport,
    hasMore: Boolean,
    loadingMore: Boolean,
    loadError: String?,
    loadMore: () -> Unit,
    edit: (Photo, PhotoViewport) -> Unit,
) {
    if (photos.isEmpty()) {
        LaunchedEffect(hasMore, loadingMore, loadError) {
            when {
                hasMore && !loadingMore && loadError == null -> loadMore()
                !hasMore -> close()
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loadError == null) CircularProgressIndicator() else Text("無法載入圖片")
        }
        return
    }
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
    var showInfo by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    val viewports = remember { mutableStateMapOf<Long, PhotoViewport>() }
    LaunchedEffect(controlsVisible) {
        setControlsVisible(controlsVisible)
    }
    val currentPage = pagerState.currentPage.coerceIn(0, photos.lastIndex)
    val currentPhoto = photos[currentPage]

    LaunchedEffect(pagerState, photos, initialIndex) {
        val validPage = initialIndex.coerceIn(0, photos.lastIndex)
        photos.getOrNull(validPage)?.let { photo ->
            if (!viewports.containsKey(photo.id)) viewports[photo.id] = initialViewport
        }
        if (validPage != pagerState.currentPage) pagerState.scrollToPage(validPage)
        snapshotFlow {
            val page = pagerState.settledPage.coerceIn(0, photos.lastIndex)
            val photo = photos[page]
            photo.id to (viewports[photo.id] ?: PhotoViewport())
        }.distinctUntilChanged().collect { (photoId, viewport) ->
            photos.firstOrNull { it.id == photoId }?.let { photo ->
                onPageChanged(photo, viewport)
            }
        }
    }

    LaunchedEffect(pagerState, photos.size, hasMore, loadingMore, loadError) {
        if (!hasMore || loadingMore || loadError != null) return@LaunchedEffect
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page >= photos.lastIndex - 3) loadMore()
            }
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
