package com.clairedoc.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clairedoc.app.BuildConfig
import com.clairedoc.app.data.model.ModelVariant
import com.clairedoc.app.data.repository.DocumentSessionRepository
import com.clairedoc.app.engine.EngineState
import com.clairedoc.app.engine.LiteRTEngine
import com.clairedoc.app.engine.ModelDownloadManager
import com.clairedoc.app.rag.ChunkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = true,
    val modelName: String = "—",
    val backend: String = "—",
    val embeddingReady: Boolean = false,
    val docCount: Int = 0,
    val chunkCount: Int = 0,
    val indexStorageMb: Float = 0f,
    val appVersion: String = "—"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val liteRTEngine: LiteRTEngine,
    private val modelDownloadManager: ModelDownloadManager,
    private val sessionRepository: DocumentSessionRepository,
    private val chunkRepository: ChunkRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadSettings() }
    }

    private suspend fun loadSettings() = withContext(Dispatchers.IO) {
        // ── AI model ──────────────────────────────────────────────────────────
        val installedVariant = ModelVariant.entries.firstOrNull {
            modelDownloadManager.isVariantInstalled(it)
        }
        val modelName = installedVariant?.displayName ?: "Not installed"

        val backend = when (val state = liteRTEngine.state.value) {
            is EngineState.Ready       -> if (state.isGpu) "GPU (Adreno 642L)" else "CPU"
            is EngineState.Initializing -> "Loading…"
            is EngineState.Error        -> "Error — ${state.message.take(40)}"
            else                        -> if (installedVariant != null) "Not started" else "—"
        }

        val embeddingReady = chunkRepository.isEmbedderReady()

        // ── Documents & index ────────────────────────────────────────────────
        val docCount = sessionRepository.getAllSessionsSnapshot().size
        val chunkCount = chunkRepository.getTotalChunkCount()

        // ObjectBox default path: context.filesDir/objectbox/
        val objectBoxDir = File(context.filesDir, "objectbox")
        val indexStorageMb = objectBoxDir.takeIf { it.exists() }
            ?.walkTopDown()
            ?.filter { it.isFile }
            ?.sumOf { it.length() }
            ?.let { it / (1024f * 1024f) }
            ?: 0f

        // ── App metadata ──────────────────────────────────────────────────────
        val appVersion = BuildConfig.VERSION_NAME

        _uiState.value = SettingsUiState(
            isLoading = false,
            modelName = modelName,
            backend = backend,
            embeddingReady = embeddingReady,
            docCount = docCount,
            chunkCount = chunkCount,
            indexStorageMb = indexStorageMb,
            appVersion = appVersion
        )
    }
}
