package com.miniphoto.viewer

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miniphoto.viewer.ui.GalleryApp
import com.miniphoto.viewer.ui.theme.MiniPhotoTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

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
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme && !photoViewerVisible
                    isAppearanceLightNavigationBars = !darkTheme && !photoViewerVisible
                }
            }

            MiniPhotoTheme(darkTheme = darkTheme) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val externalPhotoRequest by viewModel.externalPhotoRequest.collectAsStateWithLifecycle()
                var permissionGranted by remember { mutableStateOf(hasImagePermission()) }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    permissionGranted = hasImagePermission()
                    if (permissionGranted) viewModel.refresh()
                }
                val deleteLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) viewModel.refresh()
                }

                LaunchedEffect(permissionGranted) {
                    if (permissionGranted) viewModel.refresh()
                }

                GalleryApp(
                    state = state,
                    hasPermission = permissionGranted,
                    requestPermission = { permissionLauncher.launch(requiredPermissions()) },
                    refresh = viewModel::refresh,
                    share = { sharePhoto(it) },
                    delete = { uri -> deletePhoto(uri, deleteLauncher::launch) },
                    externalPhotoRequest = externalPhotoRequest,
                    clearExternalPhoto = viewModel::clearExternalPhoto,
                    displayMode = displayMode,
                    setDisplayMode = viewModel::setDisplayMode,
                    setPhotoViewerVisible = { photoViewerVisible = it },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasImagePermission()) viewModel.refresh()
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
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
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

    private fun sharePhoto(uri: Uri) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "分享圖片"))
    }

    private fun deletePhoto(uri: Uri, launchRequest: (IntentSenderRequest) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sender = MediaStore.createDeleteRequest(contentResolver, listOf(uri)).intentSender
            launchRequest(IntentSenderRequest.Builder(sender).build())
            return
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            deletePhotoOnAndroid10(uri, launchRequest)
            return
        }
        contentResolver.delete(uri, null, null)
        viewModel.refresh()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deletePhotoOnAndroid10(uri: Uri, launchRequest: (IntentSenderRequest) -> Unit) {
        try {
            contentResolver.delete(uri, null, null)
            viewModel.refresh()
        } catch (error: RecoverableSecurityException) {
            launchRequest(IntentSenderRequest.Builder(error.userAction.actionIntent.intentSender).build())
        }
    }
}
