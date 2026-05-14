package com.clairedoc.app.rag

import android.util.Log
import com.clairedoc.app.data.db.DocumentSession
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
    private val embeddingEngine: EmbeddingEngine
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

        val title = session.userTitle ?: session.documentType
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

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds a plain-text corpus from the session's JSON fields.
     *
     * JSON array brackets, quotes, and commas are stripped so the chunker
     * sees natural prose rather than raw JSON syntax.
     */
    private fun buildSessionText(session: DocumentSession): String {
        fun cleanJson(json: String): String = json.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .replace(Regex("\"\\s*,\\s*\""), ".\n")
            .replace("\"", "")
            .trim()

        return buildString {
            append("Document type: ${session.documentType}.\n")
            append(cleanJson(session.summaryJson))
            append("\n")
            append(cleanJson(session.actionsJson))
            append("\n")
            append(cleanJson(session.risksJson))
        }
    }
}
