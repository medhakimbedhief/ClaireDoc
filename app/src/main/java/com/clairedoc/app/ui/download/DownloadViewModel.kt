package com.clairedoc.app.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clairedoc.app.data.model.DownloadProgress
import com.clairedoc.app.engine.LiteRTEngine
import com.clairedoc.app.engine.ModelDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DownloadUiState {
    object Idle : DownloadUiState()
    data class Downloading(val progress: DownloadProgress) : DownloadUiState()
    object Initializing : DownloadUiState()  // engine loading after download
    object Complete : DownloadUiState()
    data class Failed(val message: String) : DownloadUiState()
}

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadManager: ModelDownloadManager,
    private val engine: LiteRTEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    init {
        if (engine.isModelPresent) {
            initEngine()
        } else {
            resumeOrWait()
        }
    }

    private fun resumeOrWait() {
        val savedId = downloadManager.getSavedDownloadId()
        if (savedId != -1L) {
            observeDownload(savedId)
        }
        // else: waiting for user to tap "Download"
    }

    fun startDownload() {
        val id = downloadManager.startDownload()
        observeDownload(id)
    }

    private fun observeDownload(downloadId: Long) {
        viewModelScope.launch {
            downloadManager.observeProgress(downloadId).collect { progress ->
                when {
                    progress.isComplete -> {
                        _uiState.value = DownloadUiState.Initializing
                        initEngine()
                    }
                    progress.isFailed -> {
                        _uiState.value = DownloadUiState.Failed(
                            "Download failed. Check your connection and retry."
                        )
                    }
                    else -> {
                        _uiState.value = DownloadUiState.Downloading(progress)
                    }
                }
            }
        }
    }

    private fun initEngine() {
        viewModelScope.launch {
            engine.initialize()
            _uiState.value = DownloadUiState.Complete
        }
    }

    fun retry() {
        _uiState.value = DownloadUiState.Idle
        startDownload()
    }
}
