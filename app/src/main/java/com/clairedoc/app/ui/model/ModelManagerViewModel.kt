package com.clairedoc.app.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clairedoc.app.data.model.DownloadProgress
import com.clairedoc.app.data.model.ModelVariant
import com.clairedoc.app.engine.EngineState
import com.clairedoc.app.engine.LiteRTEngine
import com.clairedoc.app.engine.ModelDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class VariantUiState {
    /** No model file on disk. */
    object NotInstalled : VariantUiState()
    /** Model file present and valid. */
    object Installed : VariantUiState()
    /** Download in progress. */
    data class Downloading(val progress: DownloadProgress) : VariantUiState()
    /** Last download attempt failed. */
    data class Failed(val message: String) : VariantUiState()
    /** File deletion in progress. */
    object Deleting : VariantUiState()
}

@HiltViewModel
class ModelManagerViewModel @Inject constructor(
    private val downloadManager: ModelDownloadManager,
    private val engine: LiteRTEngine
) : ViewModel() {

    // One StateFlow per known variant — keyed by ModelVariant.
    private val _states: Map<ModelVariant, MutableStateFlow<VariantUiState>> =
        ModelVariant.entries.associateWith { variant ->
            MutableStateFlow(
                if (downloadManager.isVariantInstalled(variant)) VariantUiState.Installed
                else VariantUiState.NotInstalled
            )
        }

    /** Read-only view exposed to the UI. */
    val states: Map<ModelVariant, StateFlow<VariantUiState>> =
        _states.mapValues { it.value.asStateFlow() }

    init {
        // Resume any download that was running before this ViewModel was created
        // (e.g. app was killed mid-download and relaunched).
        val savedId = downloadManager.getSavedDownloadId()
        val savedVariant = downloadManager.getSavedDownloadVariant()
        if (savedId != -1L && savedVariant != null &&
            !downloadManager.isVariantInstalled(savedVariant)
        ) {
            setState(savedVariant, VariantUiState.Downloading(
                DownloadProgress(0L, 0L, isComplete = false, isFailed = false)
            ))
            observeDownload(savedId, savedVariant)
        }
    }

    private fun setState(variant: ModelVariant, state: VariantUiState) {
        _states[variant]?.value = state
    }

    // ──────────────────────────────────────────────────────────
    //  Public actions
    // ──────────────────────────────────────────────────────────

    fun download(variant: ModelVariant) {
        // Don't allow two simultaneous downloads
        val alreadyDownloading = _states.any { (v, flow) ->
            v != variant && flow.value is VariantUiState.Downloading
        }
        if (alreadyDownloading) return

        setState(variant, VariantUiState.Downloading(
            DownloadProgress(0L, 0L, isComplete = false, isFailed = false)
        ))
        val id = downloadManager.startDownload(variant)
        observeDownload(id, variant)
    }

    fun cancelDownload(variant: ModelVariant) {
        val savedId = downloadManager.getSavedDownloadId()
        if (savedId != -1L) downloadManager.cancelDownload(savedId)
        setState(variant, VariantUiState.NotInstalled)
    }

    fun deleteModel(variant: ModelVariant) {
        setState(variant, VariantUiState.Deleting)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                downloadManager.deleteModel(variant)
                // Release the engine so it doesn't hold a handle to the deleted file
                if (engine.state.value is EngineState.Ready) {
                    engine.close()
                }
            }
            setState(variant, VariantUiState.NotInstalled)
        }
    }

    fun retryDownload(variant: ModelVariant) {
        setState(variant, VariantUiState.NotInstalled)
        download(variant)
    }

    // ──────────────────────────────────────────────────────────
    //  Private helpers
    // ──────────────────────────────────────────────────────────

    private fun observeDownload(downloadId: Long, variant: ModelVariant) {
        viewModelScope.launch {
            downloadManager.observeProgress(downloadId).collect { progress ->
                when {
                    progress.isComplete -> {
                        setState(variant, VariantUiState.Installed)
                        // Warm up the engine with the newly downloaded model if it isn't ready
                        if (engine.state.value !is EngineState.Ready) {
                            engine.initialize()
                        }
                    }
                    progress.isFailed -> setState(
                        variant,
                        VariantUiState.Failed("Download failed. Check your connection and retry.")
                    )
                    else -> setState(variant, VariantUiState.Downloading(progress))
                }
            }
        }
    }
}
