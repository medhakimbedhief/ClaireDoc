package com.clairedoc.app.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clairedoc.app.data.model.SourceType
import com.clairedoc.app.data.repository.DocumentSessionRepository
import com.clairedoc.app.pipeline.DocumentAnalyzer
import com.google.gson.Gson
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

sealed class ScanUiState {
    object Idle : ScanUiState()
    object Scanning : ScanUiState()
    object Analyzing : ScanUiState()
    data class Success(val resultJson: String, val sessionId: String) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentAnalyzer: DocumentAnalyzer,
    private val repository: DocumentSessionRepository,
    private val gson: Gson
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    // Tracks which button opened the ML Kit scanner so the source type is correct.
    private var pendingSourceType: SourceType = SourceType.CAMERA

    fun onScannerOpened(sourceType: SourceType = SourceType.CAMERA) {
        pendingSourceType = sourceType
        _uiState.value = ScanUiState.Scanning
    }

    /** Camera scan or ML Kit gallery import. */
    fun analyzeDocument(imageUri: Uri) {
        _uiState.value = ScanUiState.Analyzing
        viewModelScope.launch(Dispatchers.IO) {
            dispatchAnalysis(
                analysisUri = imageUri,
                sourceUri = imageUri.toString(),
                sourceType = pendingSourceType
            )
        }
    }

    /**
     * Renders page 1 of a user-selected PDF via [PdfRenderer] (no extra library),
     * then runs the same analysis pipeline as a camera scan.
     */
    fun analyzePdf(uri: Uri) {
        _uiState.value = ScanUiState.Analyzing
        viewModelScope.launch(Dispatchers.IO) {
            val jpegFile = renderPdfFirstPage(uri)
            if (jpegFile == null) {
                _uiState.value = ScanUiState.Error(
                    "Could not read this PDF. Is it valid and not password-protected?"
                )
                return@launch
            }
            try {
                dispatchAnalysis(
                    analysisUri = Uri.fromFile(jpegFile),
                    sourceUri = uri.toString(),
                    sourceType = SourceType.PDF
                )
            } finally {
                jpegFile.delete()
            }
        }
    }

    fun clearError() {
        _uiState.value = ScanUiState.Idle
    }

    // ──────────────────────────────────────────────────────────
    //  Private helpers
    // ──────────────────────────────────────────────────────────

    private suspend fun dispatchAnalysis(
        analysisUri: Uri,
        sourceUri: String,
        sourceType: SourceType
    ) {
        when (val result = documentAnalyzer.analyze(analysisUri, context)) {
            is DocumentAnalyzer.AnalysisResult.Success -> {
                val sessionId = repository.saveSession(result.result, sourceUri, sourceType)
                val json = gson.toJson(result.result)
                _uiState.value = ScanUiState.Success(json, sessionId)
            }
            is DocumentAnalyzer.AnalysisResult.Failure -> {
                _uiState.value = ScanUiState.Error(
                    result.message.ifBlank { "Analysis failed. Please try again." }
                )
            }
        }
    }

    private suspend fun renderPdfFirstPage(uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext null
            pfd.use { parcelFd ->
                PdfRenderer(parcelFd).use { renderer ->
                    if (renderer.pageCount == 0) return@use null
                    renderer.openPage(0).use { page ->
                        val bitmap = Bitmap.createBitmap(
                            page.width, page.height, Bitmap.Config.ARGB_8888
                        )
                        page.render(
                            bitmap, null, null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        val outFile = File(
                            context.cacheDir,
                            "pdf_page_${System.currentTimeMillis()}.jpg"
                        )
                        outFile.outputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        bitmap.recycle()
                        outFile
                    }
                }
            }
        }.getOrNull()
    }
}
