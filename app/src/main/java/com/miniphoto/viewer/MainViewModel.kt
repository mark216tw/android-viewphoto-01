package com.miniphoto.viewer

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miniphoto.viewer.data.Photo
import com.miniphoto.viewer.data.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DisplayMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromStoredValue(value: String?): DisplayMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

enum class ExternalOpenMode { VIEW, EDIT }

data class ExternalPhotoRequest(
    val photo: Photo,
    val mode: ExternalOpenMode,
)

data class GalleryUiState(
    val photos: List<Photo> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PhotoRepository(application)
    private val preferences = application.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()
    private val _externalPhotoRequest = MutableStateFlow<ExternalPhotoRequest?>(null)
    val externalPhotoRequest: StateFlow<ExternalPhotoRequest?> = _externalPhotoRequest.asStateFlow()
    private val _displayMode = MutableStateFlow(
        DisplayMode.fromStoredValue(preferences.getString(DISPLAY_MODE_KEY, null))
    )
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { repository.loadPhotos() }
                .onSuccess { photos -> _uiState.value = GalleryUiState(photos = photos) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(loading = false, error = error.message ?: "無法讀取圖片")
                    }
                }
        }
    }

    fun openExternalPhoto(uri: Uri, mode: ExternalOpenMode) {
        viewModelScope.launch {
            runCatching { repository.loadExternalPhoto(uri) }
                .onSuccess { _externalPhotoRequest.value = ExternalPhotoRequest(it, mode) }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "無法開啟圖片") }
                }
        }
    }

    fun clearExternalPhoto() {
        _externalPhotoRequest.value = null
    }

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
        preferences.edit().putString(DISPLAY_MODE_KEY, mode.name).apply()
    }

    private companion object {
        const val SETTINGS_NAME = "mini_photo_settings"
        const val DISPLAY_MODE_KEY = "display_mode"
    }
}
