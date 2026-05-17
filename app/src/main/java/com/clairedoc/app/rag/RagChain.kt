package com.clairedoc.app.rag

import android.util.Log
import com.clairedoc.app.data.repository.DocumentSessionRepository
import com.clairedoc.app.engine.EngineState
import com.clairedoc.app.engine.LiteRTEngine
import com.clairedoc.app.pipeline.PromptBuilder
import com.clairedoc.app.pipeline.RagContext
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RagChain"

/** Max chunks to keep per source session after dedup. */
private const val MAX_CHUNKS_PER_SESSION = 2

/** Maximum total word count across all retrieved chunks. */
private const val MAX_WORD_BUDGET = 2000

// ── Token types ───────────────────────────────────────────────────────────────

/**
 * Discriminated union emitted by [RagChain.query].
 *
 * Emission order:
 *   [TextToken]* → [Sources] → [Suggestions]? (on success)
 *   [Error]              (on failure — flow completes immediately after)
 */
sealed class RagToken {
    /** A streaming text fragment from Gemma. */
    data class TextToken(val text: String) : RagToken()

    /**
     * Emitted once, after all [TextToken]s, carrying the ranked chunks the answer
     * was derived from.  The ViewModel maps these to SourceReference for the UI.
     */
    data class Sources(val chunks: List<RankedChunk>) : RagToken()

    /**
     * Emitted once after [Sources], carrying AI-generated follow-up suggestions.
     * Ephemeral — ViewModel stores in-memory only, never persisted.
     */
    data class Suggestions(val questions: List<String>) : RagToken()

    /** Terminal error token — the flow completes immediately after this is emitted. */
    data class Error(val message: String) : RagToken()
}

// ── RagChain ──────────────────────────────────────────────────────────────────

/**
 * Orchestrates the full Retrieval-Augmented Generation pipeline:
 *
 * 1. HNSW cross-document retrieval via [ChunkRepository.retrieveAll]
 * 2. Dedup — at most [MAX_CHUNKS_PER_SESSION] chunks per source session
 * 3. Word-budget truncation — drop lowest-ranked chunks until ≤ [MAX_WORD_BUDGET] words
 * 4. Build [RagContext] list with session metadata from [DocumentSessionRepository]
 * 5. Construct static system prompt + per-turn user message via [PromptBuilder]
 * 6. Stream Gemma tokens via [LiteRTEngine.generateText]
 * 7. Emit [RagToken.Sources] after streaming completes
 *
 * All IO work runs on [Dispatchers.IO] via [flowOn].
 */
@Singleton
class RagChain @Inject constructor(
    private val chunkRepository: ChunkRepository,
    private val engine: LiteRTEngine,
    private val promptBuilder: PromptBuilder,
    private val sessionRepository: DocumentSessionRepository,
    private val gson: Gson
) {

    /**
     * Executes a full RAG query for [question] and returns a cold [Flow] of [RagToken]s.
     *
     * The flow is cold — a new pipeline execution starts on each [collect] call.
     * Must be collected from a non-main dispatcher (or the ViewModel's [viewModelScope],
     * which uses the main dispatcher but the [flowOn] call moves IO work off it).
     *
     * @param topK initial candidate count passed to HNSW; after dedup and budget
     *             trimming the number of chunks used may be smaller.
     */
    fun query(question: String, topK: Int = 10): Flow<RagToken> = flow {

        // ── 1. Retrieve ───────────────────────────────────────────────────────
        val ranked = runCatching {
            chunkRepository.retrieveAll(question, topK = topK)
        }.getOrElse { ex ->
            Log.e(TAG, "retrieveAll failed", ex)
            emit(RagToken.Error("Retrieval failed: ${ex.message}"))
            return@flow
        }

        if (ranked.isEmpty()) {
            emit(RagToken.Error(
                "I couldn't find relevant information in your documents for that question."
            ))
            return@flow
        }

        // ── 2. Dedup — max MAX_CHUNKS_PER_SESSION per session ─────────────────
        // ranked is already ordered by descending similarity score; iterating in
        // order preserves the highest-scored chunks for each session.
        val seenCounts = mutableMapOf<String, Int>()
        val deduped = ranked.filter { rc ->
            val n = seenCounts.getOrDefault(rc.chunk.sessionId, 0)
            (n < MAX_CHUNKS_PER_SESSION).also { keep ->
                if (keep) seenCounts[rc.chunk.sessionId] = n + 1
            }
        }

        // ── 3. Word-budget truncation ─────────────────────────────────────────
        val budgeted = mutableListOf<RankedChunk>()
        var wordCount = 0
        for (rc in deduped) {
            val chunkWords = rc.chunk.text.split(Regex("\\s+")).size
            // Always include at least 1 chunk even if it alone exceeds the budget
            if (wordCount + chunkWords > MAX_WORD_BUDGET && budgeted.isNotEmpty()) break
            budgeted.add(rc)
            wordCount += chunkWords
        }
        Log.d(TAG, "RAG context: ${budgeted.size} chunk(s), ~$wordCount words")

        // ── 4. Build RagContext list ───────────────────────────────────────────
        val sessions = runCatching {
            sessionRepository.getAllSessions().first().associateBy { it.id }
        }.getOrElse { emptyMap() }

        val ragContexts = budgeted.mapNotNull { rc ->
            val session = sessions[rc.chunk.sessionId] ?: return@mapNotNull null
            RagContext(
                sessionId    = rc.chunk.sessionId,
                documentTitle = rc.chunk.documentTitle.ifBlank { session.documentType },
                documentType = session.documentType,
                chunkText    = rc.chunk.text
            )
        }

        if (ragContexts.isEmpty()) {
            // Sessions were deleted since indexing — treat as no results
            emit(RagToken.Error(
                "I couldn't find relevant information in your documents for that question."
            ))
            return@flow
        }

        // ── 5. Ensure Gemma engine is ready ───────────────────────────────────
        if (engine.state.value !is EngineState.Ready) {
            engine.initialize()
        }
        if (engine.state.value is EngineState.Error) {
            val msg = (engine.state.value as EngineState.Error).message
            emit(RagToken.Error("Engine not ready: $msg"))
            return@flow
        }

        // ── 6. Build prompts and stream ───────────────────────────────────────
        val systemPrompt = promptBuilder.buildRagSystemPrompt()
        val userMessage  = promptBuilder.buildRagUserMessage(question, ragContexts)

        val answerBuilder = StringBuilder()
        val streamResult = runCatching {
            engine.generateText(systemPrompt, userMessage)
                .collect { token ->
                    answerBuilder.append(token)
                    emit(RagToken.TextToken(token))
                }
        }

        if (streamResult.isFailure) {
            Log.e(TAG, "Gemma streaming failed", streamResult.exceptionOrNull())
            emit(RagToken.Error("Could not generate a response — please try again."))
            return@flow
        }

        // ── 7. Emit sources after all text tokens ─────────────────────────────
        emit(RagToken.Sources(budgeted))

        // ── 8. Generate follow-up suggestions (best-effort) ───────────────────
        val completedAnswer = answerBuilder.toString().trim()
        if (completedAnswer.isNotBlank()) {
            val suggestions = generateSuggestions(question, completedAnswer)
            if (suggestions.isNotEmpty()) emit(RagToken.Suggestions(suggestions))
        }

    }.flowOn(Dispatchers.IO)

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Makes a second short LiteRT-LM call to produce 3 follow-up question suggestions.
     * Returns an empty list on any failure (malformed JSON, timeout, etc.).
     */
    private suspend fun generateSuggestions(question: String, answer: String): List<String> {
        val truncatedAnswer = answer.take(500)
        val prompt = """
            Based on this question: "$question"
            And this answer: "$truncatedAnswer"
            Generate exactly 3 short follow-up questions the user might ask next.
            Respond ONLY with a JSON array of 3 strings. Max 8 words each.
            Example: ["What is the late fee?", "Who do I contact?", "Can I pay online?"]
        """.trimIndent()
        return runCatching {
            val raw = engine.generateText("", prompt).toList().joinToString("")
            gson.fromJson(raw.cleanJson(), Array<String>::class.java).toList()
        }.getOrDefault(emptyList())
    }

    /** Strips markdown code fences and extracts the first JSON array from raw model output. */
    private fun String.cleanJson(): String {
        val stripped = trimIndent()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = stripped.indexOf('[')
        val end   = stripped.lastIndexOf(']')
        return if (start != -1 && end > start) stripped.substring(start, end + 1) else stripped
    }
}
