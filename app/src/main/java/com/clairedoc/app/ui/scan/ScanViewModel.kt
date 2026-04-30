package com.clairedoc.app.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
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
    data class PdfSelected(
        val uri: Uri,
        val fileName: String,
        val fileSizeMb: Float,
        val pageCount: Int
    ) : ScanUiState()
    data class Analyzing(val step: String = "Analyzing document...") : ScanUiState()
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
        _uiState.value = ScanUiState.Analyzing()
        viewModelScope.launch(Dispatchers.IO) {
            dispatchAnalysis(
                analysisUri = imageUri,
                sourceUri = imageUri.toString(),
                sourceType = pendingSourceType
            )
        }
    }

    /**
     * Called by the SAF file picker when the user selects a PDF.
     * Validates file size, reads metadata, and transitions to [ScanUiState.PdfSelected]
     * so the user can review the file before triggering analysis.
     */
    fun onPdfSelected(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                // Check file size before doing anything heavier
                val sizePfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: error("Cannot open file")
                val sizeBytes = sizePfd.statSize
                sizePfd.close()

                if (sizeBytes > 50L * 1024 * 1024) {
                    _uiState.value = ScanUiState.Error(
                        "This file is too large. Maximum size is 50MB."
                    )
                    return@runCatching
                }
                val sizeMb = sizeBytes / (1024f * 1024f)

                val fileName = context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "document.pdf"

                // Open a fresh fd for PdfRenderer (it takes ownership and closes it)
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")!!
                val pageCount = PdfRenderer(pfd).use { it.pageCount }

                _uiState.value = ScanUiState.PdfSelected(
                    uri = uri,
                    fileName = fileName,
                    fileSizeMb = sizeMb,
                    pageCount = pageCount
                )
            }.onFailure { e ->
                _uiState.value = ScanUiState.Error(classifyPdfError(e))
            }
        }
    }

    fun clearPdfSelection() {
        _uiState.value = ScanUiState.Idle
    }

    /** Called from the Analyze button in [ScanUiState.PdfSelected] state. */
    fun startPdfAnalysis() {
        val selected = _uiState.value as? ScanUiState.PdfSelected ?: return
        val uri = selected.uri
        val pageCount = selected.pageCount
        _uiState.value = ScanUiState.Analyzing("Rendering PDF...")
        viewModelScope.launch(Dispatchers.IO) {
            val composite = renderPdfComposite(uri)
            if (composite == null) {
                _uiState.value = ScanUiState.Error(
                    "Could not read this PDF. Try saving it again."
                )
                return@launch
            }
            val (jpegFile, _) = composite
            _uiState.value = ScanUiState.Analyzing("Analyzing document...")
            try {
                dispatchAnalysis(
                    analysisUri = Uri.fromFile(jpegFile),
                    sourceUri = uri.toString(),
                    sourceType = SourceType.PDF,
                    pageCount = pageCount
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
        sourceType: SourceType,
        pageCount: Int? = null
    ) {
        when (val result = documentAnalyzer.analyze(analysisUri, context)) {
            is DocumentAnalyzer.AnalysisResult.Success -> {
                val sessionId = repository.saveSession(result.result, sourceUri, sourceType, pageCount)
                // Persist the scanned image while we still hold a valid URI grant.
                // ML Kit content:// URIs expire after the activity-result callback returns,
                // so we copy to internal storage now for use by follow-up Q&A later.
                cacheImageForQA(sessionId, analysisUri)
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

    /**
     * Copies [analysisUri] → `filesDir/qa_images/<sessionId>.jpg`.
     * This permanent file is used by ResultViewModel for follow-up Q&A so the
     * model has the original document image in context even after the one-shot
     * ContentProvider grant for the scan URI has expired.
     *
     * Failures are silently swallowed — Q&A degrades gracefully to text-only mode.
     */
    private fun cacheImageForQA(sessionId: String, analysisUri: Uri) {
        runCatching {
            val dir = File(context.filesDir, "qa_images").also { it.mkdirs() }
            val dest = File(dir, "$sessionId.jpg")
            context.contentResolver.openInputStream(analysisUri)!!.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    /**
     * Renders all pages of a PDF into a single vertically-concatenated Bitmap,
     * capped at 4096 px total height (prioritising first pages).
     * Returns the JPEG temp file and the total page count, or null on any failure.
     * The returned file lives in cacheDir; caller must delete it after use.
     */
    private suspend fun renderPdfComposite(uri: Uri): Pair<File, Int>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: return@withContext null
                pfd.use { parcelFd ->
                    PdfRenderer(parcelFd).use { renderer ->
                        val totalPages = renderer.pageCount
                        if (totalPages == 0) return@withContext null

                        val maxCompositeHeight = 4096
                        val pageBitmaps = mutableListOf<Bitmap>()
                        var totalHeight = 0
                        var maxWidth = 0
                        var stopEarly = false

                        for (i in 0 until totalPages) {
                            if (stopEarly) break
                            renderer.openPage(i).use { page ->
                                // Always include the first page; for subsequent pages stop
                                // before the composite would exceed the height cap.
                                if (totalHeight + page.height > maxCompositeHeight &&
                                    pageBitmaps.isNotEmpty()
                                ) {
                                    stopEarly = true
                                    return@use
                                }
                                val bmp = Bitmap.createBitmap(
                                    page.width, page.height, Bitmap.Config.ARGB_8888
                                )
                                page.render(
                                    bmp, null, null,
                                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                )
                                pageBitmaps.add(bmp)
                                totalHeight += page.height
                                if (page.width > maxWidth) maxWidth = page.width
                            }
                        }

                        if (pageBitmaps.isEmpty()) return@withContext null

                        val composite = Bitmap.createBitmap(
                            maxWidth, totalHeight, Bitmap.Config.ARGB_8888
                        )
                        val canvas = Canvas(composite)
                        canvas.drawColor(Color.WHITE)
                        var yOffset = 0
                        for (bmp in pageBitmaps) {
                            canvas.drawBitmap(bmp, 0f, yOffset.toFloat(), null)
                            yOffset += bmp.height
                            bmp.recycle()
                        }

                        val outFile = File(
                            context.cacheDir,
                            "pdf_composite_${System.currentTimeMillis()}.jpg"
                        )
                        outFile.outputStream().use { out ->
                            composite.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        composite.recycle()
                        Pair(outFile, totalPages)
                    }
                }
            }.getOrNull()
        }

    private fun classifyPdfError(e: Throwable): String = when {
        e is SecurityException ||
        e.message?.contains("password", ignoreCase = true) == true ->
            "This PDF is password protected. Please export an unlocked version."
        else -> "Could not read this PDF. Try saving it again."
    }
}
