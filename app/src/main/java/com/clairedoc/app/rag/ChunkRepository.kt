package com.clairedoc.app.rag

import android.util.Log
import com.clairedoc.app.data.db.DocumentSession
import com.clairedoc.app.data.model.DocumentResult
import com.google.gson.Gson
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ClaireDoc_RAG"

/**
 * A document chunk ranked by semantic similarity to a query.
 * [score] is cosine similarity ∈ (-1, 1]; higher = more relevant.
 */
data class RankedChunk(val chunk: DocumentChunk, val score: Double)

/**
 * Manages the ObjectBox vector store for document chunks.
 *
 * Typical lifecycle:
 * 1. [indexSession] after a document is analysed — chunks and embeds its text.
 * 2. [retrieve] during Q&A to fetch the most relevant passages.
 * 3. [deleteChunksForSession] when a session is deleted from Room.
 */
@Singleton
class ChunkRepository @Inject constructor(
    private val store: BoxStore,
    private val embeddingEngine: EmbeddingEngine,
    private val gson: Gson
) {
    private val box get() = store.boxFor<DocumentChunk>()

    // ── Indexing ──────────────────────────────────────────────────────────────

    /**
     * Chunks the session's plain-text corpus, embeds each chunk, and writes
     * all [DocumentChunk] entities to ObjectBox.
     *
     * Idempotent only if you call [deleteChunksForSession] first.
     * Runs on [Dispatchers.IO].
     */
    suspend fun indexSession(session: DocumentSession) = withContext(Dispatchers.IO) {
        val fullText = buildSessionText(session)
        val textChunks = Chunker.chunk(fullText)
        Log.d(TAG, "Indexing session ${session.id}: ${textChunks.size} chunk(s)")

        val title = session.displayTitle
        val entities = textChunks.mapIndexed { idx, chunkText ->
            DocumentChunk(
                sessionId = session.id,
                documentTitle = title,
                chunkIndex = idx,
                totalChunks = textChunks.size,
                text = chunkText,
                embedding = embeddingEngine.embed(chunkText)
            )
        }
        box.put(entities)
        Log.d(TAG, "Session ${session.id} indexed (${entities.size} vectors stored)")
    }

    // ── Retrieval ─────────────────────────────────────────────────────────────

    /**
     * Embeds [query] and returns the top-[topK] most semantically similar chunks
     * for [sessionId], ranked by cosine similarity (highest first).
     *
     * Uses ObjectBox HNSW approximate nearest-neighbour search. The search
     * candidate count is set to `topK * 10` (max 200) to improve recall.
     */
    suspend fun retrieve(
        sessionId: String,
        query: String,
        topK: Int = 5
    ): List<RankedChunk> = withContext(Dispatchers.IO) {
        val queryVec = embeddingEngine.embedQuery(query)
        val searchCount = minOf(topK * 10, 200)

        store.boxFor<DocumentChunk>()
            .query(DocumentChunk_.embedding.nearestNeighbors(queryVec, searchCount))
            .build()
            .findWithScores()
            .filter { it.get().sessionId == sessionId }
            .take(topK)
            .map { objectWithScore ->
                // ObjectBox returns cosine DISTANCE (lower = closer); convert to similarity
                RankedChunk(objectWithScore.get(), score = 1.0 - objectWithScore.score)
            }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Removes all indexed chunks for [sessionId] from ObjectBox.
     * Call when deleting a Room session or before re-indexing.
     */
    suspend fun deleteChunksForSession(sessionId: String) = withContext(Dispatchers.IO) {
        val toRemove = box.query(DocumentChunk_.sessionId.equal(sessionId)).build().find()
        box.remove(toRemove)
        Log.d(TAG, "Removed ${toRemove.size} chunk(s) for session $sessionId")
    }

    /**
     * Cross-document HNSW search — returns topK most relevant chunks from ALL sessions.
     * Unlike [retrieve], does NOT filter by sessionId; results span every indexed document.
     */
    suspend fun retrieveAll(
        query: String,
        topK: Int = 5
    ): List<RankedChunk> = withContext(Dispatchers.IO) {
        val queryVec = embeddingEngine.embedQuery(query)
        val searchCount = minOf(topK * 10, 200)

        store.boxFor<DocumentChunk>()
            .query(DocumentChunk_.embedding.nearestNeighbors(queryVec, searchCount))
            .build()
            .findWithScores()
            .take(topK)
            .map { objectWithScore ->
                // ObjectBox returns cosine DISTANCE (lower = closer); convert to similarity
                RankedChunk(objectWithScore.get(), score = 1.0 - objectWithScore.score)
            }
    }

    /**
     * Returns distinct sessionIds that have at least one indexed chunk.
     * Synchronous — safe to call from any thread (Room-free, ObjectBox only).
     */
    fun getIndexedSessionIds(): Set<String> =
        box.query().build().find().mapTo(mutableSetOf()) { it.sessionId }

    /**
     * Returns true if [sessionId] has at least one indexed chunk.
     * Synchronous — safe to call from any thread.
     */
    fun isIndexed(sessionId: String): Boolean =
        box.query(DocumentChunk_.sessionId.equal(sessionId)).build().count() > 0

    /**
     * True when the TFLite embedder model file is present on disk.
     * Synchronous — safe to call from any thread.
     */
    fun isEmbedderReady(): Boolean = embeddingEngine.isModelReady()

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds a plain-text corpus from the session for chunking and embedding.
     *
     * Prefers [DocumentSession.fullResultJson] (added in migration 4→5) which contains
     * all v2 fields — glossary terms, sender, contacts — giving the embedder richer
     * semantic signal. Falls back to stripping the individual JSON columns for legacy rows.
     */
    private fun buildSessionText(session: DocumentSession): String {
        // Prefer full result JSON — includes v2 fields (glossary, sender, contacts)
        if (session.fullResultJson.isNotBlank()) {
            runCatching {
                val result = gson.fromJson(session.fullResultJson, DocumentResult::class.java)
                return buildString {
                    append("Document type: ${result.documentType}.\n")
                    result.summary.forEach { append("$it\n") }
                    result.actions.forEach { a ->
                        append(a.description)
                        a.deadline?.let { append(" (deadline: $it)") }
                        append("\n")
                    }
                    result.risks.forEach { append("$it\n") }
                    result.sender?.let { s ->
                        append("From: ${s.name}")
                        s.department?.let { append(", $it") }
                        append("\n")
                    }
                    result.glossaryTerms?.forEach { g ->
                        append("${g.term}: ${g.plainExplanation}\n")
                    }
                }
            }
            // Fall through if JSON parse fails — use legacy path below
        }

        // Legacy fallback: strip JSON syntax from individual columns
        fun cleanJson(json: String): String = json.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .replace(Regex("\"\\s*,\\s*\""), ".\n")
            .replace("\"", "")
            .trim()

        return buildString {
            append("Title: ${session.displayTitle}\n")
            append("Document type: ${session.documentType}.\n")
            append(cleanJson(session.summaryJson))
            append("\n")
            append(cleanJson(session.actionsJson))
            append("\n")
            append(cleanJson(session.risksJson))
        }
    }
}
