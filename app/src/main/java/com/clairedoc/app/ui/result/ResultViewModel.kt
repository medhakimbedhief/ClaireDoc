package com.clairedoc.app.ui.result

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clairedoc.app.data.db.DocumentSession
import com.clairedoc.app.data.model.ChatMessage
import com.clairedoc.app.data.model.DocumentResult
import com.clairedoc.app.data.model.GlossaryTerm
import com.clairedoc.app.data.model.Role
import com.clairedoc.app.data.model.SessionStatus
import com.clairedoc.app.data.model.SourceType
import com.clairedoc.app.data.repository.DocumentSessionRepository
import com.clairedoc.app.engine.EngineState
import com.clairedoc.app.engine.LiteRTEngine
import com.clairedoc.app.pipeline.PromptBuilder
import com.clairedoc.app.tts.TTSManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

private const val TAG = "ResultVM"
private const val MAX_EXCHANGES = 10  // 1 exchange = 1 USER + 1 ASSISTANT message

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gson: Gson,
    private val ttsManager: TTSManager,
    private val engine: LiteRTEngine,
    private val promptBuilder: PromptBuilder,
    private val repository: DocumentSessionRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // ── Existing result state ──────────────────────────────────
    private val _result = MutableStateFlow<DocumentResult?>(null)
    val result: StateFlow<DocumentResult?> = _result.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    val sessionId: String = Uri.decode(savedStateHandle.get<String>("sessionId").orEmpty())

    // ── Q&A state ─────────────────────────────────────────────
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    /** Accumulates streaming tokens for the in-progress response. Empty when idle. */
    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    // Holds the full session row so askFollowUp can access imageUri / sourceType
    private var currentSession: DocumentSession? = null

    private val _sessionStatus = MutableStateFlow(SessionStatus.UNREAD)
    val sessionStatus: StateFlow<SessionStatus> = _sessionStatus.asStateFlow()

    private val _userTitle = MutableStateFlow<String?>(null)
    val userTitle: StateFlow<String?> = _userTitle.asStateFlow()

    private val _showConfetti = MutableStateFlow(false)
    val showConfetti: StateFlow<Boolean> = _showConfetti.asStateFlow()

    // ── Init ──────────────────────────────────────────────────
    init {
        val encoded = savedStateHandle.get<String>("resultJson").orEmpty()
        val json = Uri.decode(encoded)
        if (json.isNotBlank()) {
            runCatching { gson.fromJson(json, DocumentResult::class.java) }
                .onSuccess { _result.value = it }
        }

        // Keep chat history in sync with Room
        if (sessionId.isNotBlank()) {
            viewModelScope.launch {
                repository.getSession(sessionId).collect { session ->
                    if (session != null) {
                        currentSession = session
                        _chatMessages.value = parseChatHistory(session.chatHistoryJson)
                        _sessionStatus.value = session.status
                        _userTitle.value = session.userTitle
                    }
                }
            }
        }
    }

    // ── TTS ───────────────────────────────────────────────────
    fun toggleTTS() {
        if (_isSpeaking.value) {
            ttsManager.stop()
            _isSpeaking.value = false
        } else {
            val r = _result.value ?: return
            startReading(r)
        }
    }

    private fun startReading(result: DocumentResult) {
        _isSpeaking.value = true
        // Delegates utterance ordering, locale switching, and confidence warning
        // to TTSManager.speak(DocumentResult). The lambda runs on the TTS thread
        // when the last utterance finishes naturally (stop() handles early cancel).
        ttsManager.speak(result) { _isSpeaking.value = false }
    }

    // ── Glossary TTS ──────────────────────────────────────────
    fun speakTerm(term: GlossaryTerm) {
        ttsManager.speakTerm(term, _result.value?.detectedLanguage)
    }

    // ── Q&A ───────────────────────────────────────────────────

    /**
     * Sends [question] to the model, keeping the original document image in context.
     *
     * 1. Appends a USER message to the chat history (in-memory + Room)
     * 2. Sets loading / streaming state
     * 3. Resolves the document image to a temp file (falls back to text-only)
     * 4. Streams tokens → [streamingText] updates in real-time
     * 5. Finalises by appending ASSISTANT message and capping at [MAX_EXCHANGES]
     */
    fun askFollowUp(question: String) {
        val session = currentSession ?: return
        val result = _result.value ?: return
        if (_isChatLoading.value) return

        viewModelScope.launch {
            // Step 1 — user message
            val userMsg = ChatMessage(role = Role.USER, content = question)
            val history = _chatMessages.value + userMsg
            _chatMessages.value = history
            persistHistory(session.id, history)

            // Step 2 — loading
            _isChatLoading.value = true
            _streamingText.value = ""

            // Step 3 — ensure engine ready
            if (engine.state.value !is EngineState.Ready) {
                engine.initialize()
            }
            if (engine.state.value is EngineState.Error) {
                val err = engine.state.value as EngineState.Error
                finishWithError(session.id, history, "Engine not ready: ${err.message}")
                return@launch
            }

            // Step 4 — resolve image (best-effort; null → text-only)
            val imageFile = withContext(Dispatchers.IO) { resolveImageFile(session) }

            // Step 5 — stream
            val analysisJson = gson.toJson(result)
            val systemPrompt = promptBuilder.buildFollowUpSystemPrompt(analysisJson)
            val userPrompt = promptBuilder.buildFollowUpUserMessage(question)
            val sb = StringBuilder()

            val streamResult = runCatching {
                engine.generateResponse(
                    imageFile = imageFile,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt
                ).collect { token ->
                    sb.append(token)
                    _streamingText.value = sb.toString()
                }
            }

            // Step 6 — finalise
            _streamingText.value = ""
            _isChatLoading.value = false

            if (streamResult.isFailure) {
                Log.e(TAG, "Follow-up inference failed", streamResult.exceptionOrNull())
                finishWithError(session.id, history, "Could not generate a response — please try again.")
                return@launch
            }

            val assistantMsg = ChatMessage(role = Role.ASSISTANT, content = sb.toString().trim())
            val capped = capHistory(history + assistantMsg)
            _chatMessages.value = capped
            persistHistory(session.id, capped)
        }
    }

    // ── Status & title ────────────────────────────────────────

    fun updateStatus(status: SessionStatus) {
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            repository.updateSessionStatus(sessionId, status)
            if (status == SessionStatus.DONE) _showConfetti.value = true
        }
    }

    fun renameSession(title: String) {
        if (sessionId.isBlank() || title.isBlank()) return
        viewModelScope.launch { repository.renameSession(sessionId, title) }
    }

    fun dismissConfetti() { _showConfetti.value = false }

    // ── Private helpers ───────────────────────────────────────

    /**
     * Resolves the document image to a local JPEG file for multimodal inference.
     *
     * Strategy (in priority order):
     * 1. Permanent cache written by [ScanViewModel.cacheImageForQA] at scan time —
     *    always accessible, no URI permission needed.
     * 2. Original URI fallback (may fail for expired ML Kit grants).
     * 3. null → engine runs text-only with the analysis JSON as context.
     */
    private suspend fun resolveImageFile(session: DocumentSession): File? =
        withContext(Dispatchers.IO) {
            // 1. Permanent cached copy
            val cached = File(context.filesDir, "qa_images/${session.id}.jpg")
            if (cached.exists() && cached.length() > 0L) {
                Log.d(TAG, "Using cached image for Q&A: ${cached.absolutePath}")
                return@withContext cached
            }

            // 2. Fall back to original URI (works for some content schemes)
            runCatching {
                val uri = Uri.parse(session.imageUri)
                when (session.sourceType) {
                    SourceType.PDF -> renderPdfFirstPage(uri)
                    else           -> copyUriToTemp(uri)
                }
            }.getOrElse { ex ->
                Log.w(TAG, "Image not accessible for follow-up; using text-only context", ex)
                null
            }
        }

    private fun copyUriToTemp(uri: Uri): File {
        val tmp = File(context.cacheDir, "qa_img_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        return tmp
    }

    private fun renderPdfFirstPage(uri: Uri): File? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        return pfd.use { parcelFd ->
            PdfRenderer(parcelFd).use { renderer ->
                if (renderer.pageCount == 0) return@use null
                renderer.openPage(0).use { page ->
                    val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val out = File(context.cacheDir, "qa_pdf_${System.currentTimeMillis()}.jpg")
                    out.outputStream().use { s -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, s) }
                    bmp.recycle()
                    out
                }
            }
        }
    }

    private fun finishWithError(sessionId: String, history: List<ChatMessage>, msg: String) {
        val errMsg = ChatMessage(role = Role.ASSISTANT, content = msg)
        val updated = history + errMsg
        _chatMessages.value = updated
        _isChatLoading.value = false
        _streamingText.value = ""
        viewModelScope.launch { persistHistory(sessionId, updated) }
    }

    /** Keeps at most [MAX_EXCHANGES] pairs (USER + ASSISTANT) by dropping the oldest. */
    private fun capHistory(messages: List<ChatMessage>): List<ChatMessage> {
        val maxMessages = MAX_EXCHANGES * 2
        return if (messages.size > maxMessages) messages.drop(messages.size - maxMessages)
        else messages
    }

    private suspend fun persistHistory(id: String, messages: List<ChatMessage>) {
        runCatching { repository.updateChatHistory(id, messages) }
            .onFailure { Log.e(TAG, "Failed to persist chat history", it) }
    }

    private fun parseChatHistory(json: String): List<ChatMessage> {
        val type = object : TypeToken<List<ChatMessage>>() {}.type
        return runCatching { gson.fromJson<List<ChatMessage>>(json, type) }
            .getOrElse { emptyList() }
    }

    // ── Lifecycle ─────────────────────────────────────────────
    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
    }
}
