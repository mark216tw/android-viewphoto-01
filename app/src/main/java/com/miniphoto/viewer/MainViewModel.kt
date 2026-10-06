package com.miniphoto.viewer

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miniphoto.viewer.data.Photo
import com.miniphoto.viewer.data.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
    val loadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PhotoRepository(application)
    private val preferences = application.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()
    private val _externalPhotoRequest = MutableStateFlow<ExternalPhotoRequest?>(null)
    val externalPhotoRequest: StateFlow<ExternalPhotoRequest?> = _externalPhotoRequest.asStateFlow()
    private val _hasImagePermission = MutableStateFlow(false)
    val hasImagePermission: StateFlow<Boolean> = _hasImagePermission.asStateFlow()
    private val _hasWritePermission = MutableStateFlow(true)
    val hasWritePermission: StateFlow<Boolean> = _hasWritePermission.asStateFlow()
    private var pageJob: Job? = null
    private var observerJob: Job? = null
    private var nextOffset = 0
    private val _displayMode = MutableStateFlow(
        DisplayMode.fromStoredValue(preferences.getString(DISPLAY_MODE_KEY, null))
    )
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()
    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            observerJob?.cancel()
            observerJob = viewModelScope.launch {
                delay(MEDIA_CHANGE_DEBOUNCE_MILLIS)
                if (_hasImagePermission.value) refresh()
            }
        }
    }

    init {
        repository.registerPhotoObserver(mediaObserver)
    }

    fun refresh() {
        pageJob?.cancel()
        nextOffset = 0
        val refreshLimit = maxOf(PAGE_SIZE, _uiState.value.photos.size)
        pageJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, loadingMore = false, error = null) }
            try {
                val page = repository.loadPhotos(offset = 0, limit = refreshLimit)
                nextOffset = page.photos.size
                _uiState.value = GalleryUiState(
                    photos = page.photos,
                    hasMore = page.hasMore,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(loading = false, error = error.message ?: "無法讀取圖片")
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (!_hasImagePermission.value || state.loading || state.loadingMore || !state.hasMore || pageJob?.isActive == true) {
            return
        }
        pageJob = viewModelScope.launch {
            _uiState.update { it.copy(loadingMore = true, error = null) }
            try {
                val page = repository.loadPhotos(offset = nextOffset, limit = PAGE_SIZE)
                nextOffset += page.photos.size
                _uiState.update { current ->
                    current.copy(
                        photos = (current.photos + page.photos).distinctBy { it.uri },
                        loadingMore = false,
                        hasMore = page.hasMore,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(loadingMore = false, error = error.message ?: "無法載入更多圖片")
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

    fun setImagePermission(granted: Boolean) {
        _hasImagePermission.value = granted
    }

    fun setWritePermission(granted: Boolean) {
        _hasWritePermission.value = granted
    }

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
        preferences.edit().putString(DISPLAY_MODE_KEY, mode.name).apply()
    }

    override fun onCleared() {
        repository.unregisterPhotoObserver(mediaObserver)
        super.onCleared()
    }

    private companion object {
        const val PAGE_SIZE = 120
        const val MEDIA_CHANGE_DEBOUNCE_MILLIS = 350L
        const val SETTINGS_NAME = "mini_photo_settings"
        const val DISPLAY_MODE_KEY = "display_mode"
    }
}
