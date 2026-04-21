package com.clairedoc.app.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    object Scanning : ScanUiState()    // scanner UI is open
    object Analyzing : ScanUiState()   // LiteRT inference running
    data class Success(val resultJson: String) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentAnalyzer: DocumentAnalyzer,
    private val gson: Gson
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun onScannerOpened() {
        _uiState.value = ScanUiState.Scanning
    }

    /** Called after ML Kit camera scan or gallery image pick. */
    fun analyzeDocument(imageUri: Uri) {
        _uiState.value = ScanUiState.Analyzing
        viewModelScope.launch(Dispatchers.IO) {
            dispatchAnalysis(imageUri)
        }
    }

    /**
     * Picks up a PDF [uri] from the Storage Access Framework, renders its first page
     * to a JPEG using the built-in [PdfRenderer], then runs the same analysis pipeline
     * as a camera scan. No extra libraries required (PdfRenderer is API 21+).
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
                dispatchAnalysis(Uri.fromFile(jpegFile))
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

    private suspend fun dispatchAnalysis(uri: Uri) {
        when (val result = documentAnalyzer.analyze(uri, context)) {
            is DocumentAnalyzer.AnalysisResult.Success -> {
                val json = gson.toJson(result.result)
                _uiState.value = ScanUiState.Success(json)
            }
            is DocumentAnalyzer.AnalysisResult.Failure -> {
                _uiState.value = ScanUiState.Error(
                    result.message.ifBlank { "Analysis failed. Please try again." }
                )
            }
        }
    }

    /**
     * Renders page 0 of the PDF at [uri] to a temporary JPEG in [Context.getCacheDir].
     * Returns null if the PDF cannot be opened, is empty, or rendering fails.
     */
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
