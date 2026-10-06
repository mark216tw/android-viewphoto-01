package com.miniphoto.viewer

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.miniphoto.viewer.ui.GalleryApp
import com.miniphoto.viewer.ui.theme.MiniPhotoTheme
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var pendingRecoverableDeleteUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleViewIntent(intent)
        setContent {
            val displayMode by viewModel.displayMode.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (displayMode) {
                DisplayMode.SYSTEM -> systemDark
                DisplayMode.LIGHT -> false
                DisplayMode.DARK -> true
            }
            var photoViewerVisible by remember { mutableStateOf(false) }
            var viewerControlsVisible by remember { mutableStateOf(true) }
            val view = LocalView.current
            val immersiveViewer = photoViewerVisible && !viewerControlsVisible
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    val useLightIcons = !darkTheme && !photoViewerVisible
                    isAppearanceLightStatusBars = useLightIcons
                    isAppearanceLightNavigationBars = useLightIcons
                }
            }
            LaunchedEffect(immersiveViewer) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (immersiveViewer) {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }

            MiniPhotoTheme(darkTheme = darkTheme) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val externalPhotoRequest by viewModel.externalPhotoRequest.collectAsStateWithLifecycle()
                val permissionGranted by viewModel.hasImagePermission.collectAsStateWithLifecycle()
                val writePermissionGranted by viewModel.hasWritePermission.collectAsStateWithLifecycle()
                var pendingDeleteUri by remember { mutableStateOf<Uri?>(null) }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    val granted = hasImagePermission()
                    viewModel.setImagePermission(granted)
                    if (granted) viewModel.refresh()
                }
                val writePermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> viewModel.setWritePermission(granted) }
                val deleteLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    val pendingUri = pendingRecoverableDeleteUri
                    pendingRecoverableDeleteUri = null
                    if (result.resultCode == RESULT_OK) {
                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && pendingUri != null) {
                            retryDeleteOnAndroid10(pendingUri)
                        } else {
                            viewModel.refresh()
                        }
                    }
                }
                LaunchedEffect(writePermissionGranted) {
                    if (writePermissionGranted) {
                        pendingDeleteUri?.let { uri ->
                            pendingDeleteUri = null
                            deletePhoto(uri, deleteLauncher::launch)
                        }
                    }
                }

                GalleryApp(
                    state = state,
                    hasPermission = permissionGranted,
                    requestPermission = { permissionLauncher.launch(requiredPermissions()) },
                    refresh = viewModel::refresh,
                    loadMore = viewModel::loadMore,
                    share = { sharePhoto(it) },
                    delete = { uri ->
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && !writePermissionGranted) {
                            pendingDeleteUri = uri
                            writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            deletePhoto(uri, deleteLauncher::launch)
                        }
                    },
                    externalPhotoRequest = externalPhotoRequest,
                    clearExternalPhoto = viewModel::clearExternalPhoto,
                    displayMode = displayMode,
                    setDisplayMode = viewModel::setDisplayMode,
                    setPhotoViewerVisible = { photoViewerVisible = it },
                    setPhotoViewerControlsVisible = { viewerControlsVisible = it },
                    finishExternal = ::finishExternal,
                    externalEditSaved = ::finishExternalEdit,
                    writePermissionGranted = writePermissionGranted,
                    requestWritePermission = {
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                            writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val granted = hasImagePermission()
        viewModel.setImagePermission(granted)
        viewModel.setWritePermission(hasWritePermission())
        if (granted) viewModel.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        val mode = when (intent?.action) {
            Intent.ACTION_VIEW -> ExternalOpenMode.VIEW
            Intent.ACTION_EDIT -> ExternalOpenMode.EDIT
            else -> return
        }
        intent.data?.let { viewModel.openExternalPhoto(it, mode) }
    }

    private fun finishExternal() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun finishExternalEdit(uri: Uri) {
        setResult(RESULT_OK, Intent().apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        finish()
    }

    private fun imagePermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasImagePermission(): Boolean {
        val fullAccess = ContextCompat.checkSelfPermission(this, imagePermission()) ==
            PackageManager.PERMISSION_GRANTED
        val selectedAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ) == PackageManager.PERMISSION_GRANTED
        return fullAccess || selectedAccess
    }

    private fun hasWritePermission(): Boolean = Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    private fun sharePhoto(uri: Uri) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "分享圖片"))
    }

    private fun deletePhoto(uri: Uri, launchRequest: (IntentSenderRequest) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val sender = MediaStore.createDeleteRequest(contentResolver, listOf(uri)).intentSender
                launchRequest(IntentSenderRequest.Builder(sender).build())
            } catch (error: SecurityException) {
                showDeleteError("無法請求刪除圖片")
            }
            return
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            deletePhotoOnAndroid10(uri, launchRequest)
            return
        }
        lifecycleScope.launch {
            try {
                val deleted = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    contentResolver.delete(uri, null, null)
                }
                if (deleted > 0) viewModel.refresh() else showDeleteError("找不到要刪除的圖片")
            } catch (error: SecurityException) {
                showDeleteError("沒有刪除圖片的權限")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deletePhotoOnAndroid10(uri: Uri, launchRequest: (IntentSenderRequest) -> Unit) {
        lifecycleScope.launch {
            try {
                val deleted = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    contentResolver.delete(uri, null, null)
                }
                if (deleted > 0) viewModel.refresh() else showDeleteError("找不到要刪除的圖片")
            } catch (error: RecoverableSecurityException) {
                pendingRecoverableDeleteUri = uri
                launchRequest(IntentSenderRequest.Builder(error.userAction.actionIntent.intentSender).build())
            } catch (error: SecurityException) {
                showDeleteError("沒有刪除圖片的權限")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun retryDeleteOnAndroid10(uri: Uri) {
        lifecycleScope.launch {
            try {
                val deleted = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    contentResolver.delete(uri, null, null)
                }
                if (deleted > 0) viewModel.refresh() else showDeleteError("找不到要刪除的圖片")
            } catch (error: SecurityException) {
                showDeleteError("沒有刪除圖片的權限")
            }
        }
    }

    private fun showDeleteError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
