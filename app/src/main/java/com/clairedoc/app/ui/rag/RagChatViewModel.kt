package com.clairedoc.app.ui.rag

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clairedoc.app.data.db.DocumentSession
import com.clairedoc.app.data.db.toDocumentResult
import com.clairedoc.app.data.model.Role
import com.clairedoc.app.data.repository.DocumentSessionRepository
import com.clairedoc.app.engine.ModelDownloadManager
import com.clairedoc.app.rag.ChunkRepository
import com.clairedoc.app.rag.RagChain
import com.clairedoc.app.rag.RagToken
import com.clairedoc.app.rag.RankedChunk
import com.clairedoc.app.tts.TTSManager
import com.clairedoc.app.ui.NavRoutes
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private const val TAG = "RagChatVM"
private const val MAX_MESSAGES = 12  // 6 exchanges × 2

// ── Data models ───────────────────────────────────────────────────────────────

data class RagMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val sources: List<SourceReference> = emptyList()
)

data class SourceReference(
    val sessionId: String,
    val documentTitle: String,
    val urgencyLevel: String,    // "RED" | "YELLOW" | "GREEN"
    val relevantSnippet: String  // first ~200 chars of the retrieved chunk
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class RagChatViewModel @Inject constructor(
    private val chunkRepository: ChunkRepository,
    private val ragChain: RagChain,
    private val sessionRepository: DocumentSessionRepository,
    private val modelDownloadManager: ModelDownloadManager,
    private val ttsManager: TTSManager,
    private val gson: Gson
) : ViewModel() {

    // ── Session cache — updated whenever Room emits ───────────────────────────
    // Used for urgencyLevel lookup when building SourceReferences.
    private var _sessionCache: Map<String, DocumentSession> = emptyMap()

    // ── Reactive state ────────────────────────────────────────────────────────

    private val _messages = MutableStateFlow<List<RagMessage>>(emptyList())
    val messages: StateFlow<List<RagMessage>> = _messages.asStateFlow()

    /** Accumulates streaming tokens for the in-progress response. Empty when idle. */
    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * True when the EmbeddingGemma-300M TFLite model is present on disk.
     * Updated after a successful download via [triggerEmbedderDownload].
     */
    private val _embeddingReady = MutableStateFlow(modelDownloadManager.checkEmbedderExists())
    val embeddingReady: StateFlow<Boolean> = _embeddingReady.asStateFlow()

    /**
     * Download progress for the embedding model: null = idle, 0f–1f = in progress.
     */
    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    /**
     * Number of sessions that have been indexed in ObjectBox.
     * Recomputed whenever Room sessions change (e.g. after a new scan).
     */
    val indexedDocCount: StateFlow<Int> = sessionRepository.getAllSessions()
        .map { sessions -> sessions.count { chunkRepository.isIndexed(it.id) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        // Keep the session cache up to date so urgencyLevel lookups are fresh.
        viewModelScope.launch {
            sessionRepository.getAllSessions().collect { sessions ->
                _sessionCache = sessions.associateBy { it.id }
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sends [question] to Gemma 4, using cross-document HNSW retrieval as context.
     *
     * Flow:
     * 1. Append USER message
     * 2. Retrieve relevant chunks across all sessions via [ChunkRepository.retrieveAll]
     * 3. Build RAG system prompt from chunks
     * 4. Stream Gemma 4 response tokens
     * 5. Append ASSISTANT message with source citations
     * 6. Cap history at [MAX_MESSAGES] (oldest dropped)
     *
     * History is **ephemeral** — never persisted to Room.
     */
    fun sendQuery(question: String) {
        if (_isLoading.value) return

        viewModelScope.launch {
            // 1 — Append user message
            _messages.value = _messages.value + RagMessage(role = Role.USER, content = question)

            // 2 — Set loading
            _isLoading.value = true
            _streamingText.value = ""

            // 3 — Delegate the full RAG pipeline to RagChain
            var errorMsg: String? = null
            var sourcesChunks: List<RankedChunk> = emptyList()
            val sb = StringBuilder()

            ragChain.query(question).collect { token ->
                when (token) {
                    is RagToken.TextToken -> {
                        sb.append(token.text)
                        _streamingText.value = sb.toString()
                    }
                    is RagToken.Sources -> sourcesChunks = token.chunks
                    is RagToken.Error   -> errorMsg = token.message
                }
            }

            // 4 — Finalise
            _streamingText.value = ""
            _isLoading.value = false

            if (errorMsg != null) {
                appendAssistantMessage(errorMsg!!, sources = emptyList())
                return@launch
            }

            val answer = sb.toString().trim()
            val sources = buildSources(sourcesChunks)
            val assistantMsg = RagMessage(
                role = Role.ASSISTANT,
                content = answer,
                sources = sources
            )
            _messages.value = capHistory(_messages.value + assistantMsg)
        }
    }

    /**
     * Starts an automatic download of the EmbeddingGemma-300M TFLite model.
     * Uses the HF token from BuildConfig (injected from local.properties) — no
     * user input needed when the token is present at build time.
     * Does nothing if the model is already present or a download is already running.
     */
    fun triggerEmbedderDownload() {
        if (_embeddingReady.value || _downloadProgress.value != null) return
        viewModelScope.launch {
            val (modelId, _) = modelDownloadManager.startEmbedderDownload()
            if (modelId == -1L) return@launch
            modelDownloadManager.observeProgress(modelId).collect { p ->
                _downloadProgress.value = when {
                    p.isComplete || p.isFailed -> null
                    else                       -> p.fraction
                }
                if (p.isComplete) {
                    _embeddingReady.value = modelDownloadManager.checkEmbedderExists()
                }
            }
        }
    }

    /** Speaks the last ASSISTANT message aloud via TTS. */
    fun speakLastAnswer() {
        val last = _messages.value.lastOrNull { it.role == Role.ASSISTANT } ?: return
        viewModelScope.launch {
            runCatching { ttsManager.speak(last.content) }
        }
    }

    /** Clears the in-memory conversation. */
    fun clearHistory() {
        _messages.value = emptyList()
        _streamingText.value = ""
    }

    /**
     * Returns a navigation route string for [source]'s ResultScreen, or null if the
     * session is no longer in the cache. Used by the Screen to navigate on source tap.
     */
    fun getResultRouteForSource(source: SourceReference): String? {
        val session = _sessionCache[source.sessionId] ?: return null
        return runCatching {
            val result = session.toDocumentResult(gson)
            NavRoutes.resultRoute(gson.toJson(result), source.sessionId)
        }.getOrNull()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds a deduplicated [SourceReference] list from [ranked] chunks.
     * One reference per source session (first occurrence wins for snippet).
     */
    private fun buildSources(ranked: List<RankedChunk>): List<SourceReference> =
        ranked.distinctBy { it.chunk.sessionId }
            .mapNotNull { rc ->
                val session = _sessionCache[rc.chunk.sessionId] ?: return@mapNotNull null
                SourceReference(
                    sessionId = rc.chunk.sessionId,
                    documentTitle = rc.chunk.documentTitle.ifBlank { session.documentType },
                    urgencyLevel = session.urgencyLevel,
                    relevantSnippet = rc.chunk.text.take(200).trimEnd()
                )
            }

    /** Drops oldest messages when history exceeds [MAX_MESSAGES]. */
    private fun capHistory(messages: List<RagMessage>): List<RagMessage> =
        if (messages.size > MAX_MESSAGES) messages.drop(messages.size - MAX_MESSAGES)
        else messages

    /** Finalises loading state and appends an ASSISTANT message. */
    private fun appendAssistantMessage(content: String, sources: List<SourceReference>) {
        _isLoading.value = false
        _streamingText.value = ""
        val msg = RagMessage(role = Role.ASSISTANT, content = content, sources = sources)
        _messages.value = capHistory(_messages.value + msg)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
    }
}
