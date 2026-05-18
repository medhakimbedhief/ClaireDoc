package com.clairedoc.app.rag

import android.util.Log
import com.clairedoc.app.data.db.DocumentSession
import com.clairedoc.app.data.db.FtsChunkDao
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
    private val ftsDao: FtsChunkDao,
    private val gson: Gson
) {
    private val box get() = store.boxFor<DocumentChunk>()

    /**
     * Set to false after the first FTS5 failure (e.g. device SQLite compiled without fts5).
     * All subsequent FTS calls are skipped immediately, preventing repeated SQLiteLog noise.
     * Volatile because it may be written from any coroutine worker thread.
     */
    @Volatile private var ftsAvailable = true

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
        // box.put mutates each entity's @Id in-place — ids are valid immediately after
        if (ftsAvailable) {
            entities.forEach { chunk ->
                runCatching { ftsDao.insertFts(chunk.id, chunk.sessionId, chunk.text) }
                    .onFailure { e ->
                        ftsAvailable = false
                        Log.w(TAG, "FTS5 unavailable — BM25 disabled for this session: ${e.message}")
                    }
            }
        }
        Log.d(TAG, "Session ${session.id} indexed (${entities.size} vectors stored)")
    }

    // ── Retrieval ─────────────────────────────────────────────────────────────

    /**
     * Returns the top-[topK] most relevant chunks for [sessionId], fusing HNSW
     * vector search (cosine similarity) with FTS5 BM25 keyword search via RRF.
     *
     * Falls back to vector-only if FTS5 is unavailable or the sanitized query is blank.
     */
    suspend fun retrieve(
        sessionId: String,
        query: String,
        topK: Int = 5
    ): List<RankedChunk> = withContext(Dispatchers.IO) {
        val queryVec = embeddingEngine.embedQuery(query)
        val searchCount = minOf(topK * 10, 200)

        // 1. Vector search filtered to this session
        val vectorRanked = store.boxFor<DocumentChunk>()
            .query(DocumentChunk_.embedding.nearestNeighbors(queryVec, searchCount))
            .build()
            .findWithScores()
            .filter { it.get().sessionId == sessionId }
            .take(topK)
            .map { objectWithScore ->
                // ObjectBox returns cosine DISTANCE (lower = closer); convert to similarity
                RankedChunk(objectWithScore.get(), score = 1.0 - objectWithScore.score)
            }

        // 2. BM25 keyword search (filtered to session) — skip if FTS5 unavailable
        val sanitized = sanitizeFtsQuery(query)
        val bm25Ranked = if (!ftsAvailable || sanitized.isBlank()) emptyList() else
            runCatching { ftsDao.searchBm25ForSession(sanitized, sessionId, topK) }
                .onFailure { e ->
                    ftsAvailable = false
                    Log.w(TAG, "FTS5 unavailable — switching to vector-only: ${e.message}")
                }
                .getOrElse { emptyList() }

        if (bm25Ranked.isEmpty()) return@withContext vectorRanked

        // 3. Fetch chunks for BM25 IDs not already present in vector results
        val vectorIds = vectorRanked.mapTo(mutableSetOf()) { it.chunk.id }
        val extraChunks = box.get(bm25Ranked.map { it.chunkId }.filter { it !in vectorIds })
            .associateBy { it.id }

        // 4. RRF fusion
        RRF.fuse(vectorRanked, bm25Ranked, extraChunks).take(topK)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Removes all indexed chunks for [sessionId] from ObjectBox and FTS5.
     * Call when deleting a Room session or before re-indexing.
     */
    suspend fun deleteChunksForSession(sessionId: String) = withContext(Dispatchers.IO) {
        val toRemove = box.query(DocumentChunk_.sessionId.equal(sessionId)).build().find()
        box.remove(toRemove)
        if (ftsAvailable) {
            runCatching { ftsDao.deleteBySessionId(sessionId) }
                .onFailure { e ->
                    ftsAvailable = false
                    Log.w(TAG, "FTS5 unavailable — BM25 index cleanup skipped: ${e.message}")
                }
        }
        Log.d(TAG, "Removed ${toRemove.size} chunk(s) for session $sessionId")
    }

    /**
     * Cross-document hybrid search — returns topK most relevant chunks from ALL sessions,
     * fusing HNSW vector search with FTS5 BM25 via RRF.
     *
     * Falls back to vector-only if FTS5 is unavailable or the sanitized query is blank.
     */
    suspend fun retrieveAll(
        query: String,
        topK: Int = 5
    ): List<RankedChunk> = withContext(Dispatchers.IO) {
        val queryVec = embeddingEngine.embedQuery(query)
        val searchCount = minOf(topK * 10, 200)

        // 1. Vector search across all sessions
        val vectorRanked = store.boxFor<DocumentChunk>()
            .query(DocumentChunk_.embedding.nearestNeighbors(queryVec, searchCount))
            .build()
            .findWithScores()
            .take(topK)
            .map { objectWithScore ->
                // ObjectBox returns cosine DISTANCE (lower = closer); convert to similarity
                RankedChunk(objectWithScore.get(), score = 1.0 - objectWithScore.score)
            }

        // 2. BM25 keyword search across all sessions — skip if FTS5 unavailable
        val sanitized = sanitizeFtsQuery(query)
        val bm25Ranked = if (!ftsAvailable || sanitized.isBlank()) emptyList() else
            runCatching { ftsDao.searchBm25(sanitized, topK) }
                .onFailure { e ->
                    ftsAvailable = false
                    Log.w(TAG, "FTS5 unavailable — switching to vector-only: ${e.message}")
                }
                .getOrElse { emptyList() }

        if (bm25Ranked.isEmpty()) return@withContext vectorRanked

        // 3. Fetch chunks for BM25 IDs not already present in vector results
        val vectorIds = vectorRanked.mapTo(mutableSetOf()) { it.chunk.id }
        val extraChunks = box.get(bm25Ranked.map { it.chunkId }.filter { it !in vectorIds })
            .associateBy { it.id }

        // 4. RRF fusion
        RRF.fuse(vectorRanked, bm25Ranked, extraChunks).take(topK)
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
     * Returns the total number of indexed chunks across all sessions.
     * Synchronous — safe to call from any thread.
     */
    fun getTotalChunkCount(): Int = box.count().toInt()

    /**
     * True when the TFLite embedder model file is present on disk.
     * Synchronous — safe to call from any thread.
     */
    fun isEmbedderReady(): Boolean = embeddingEngine.isModelReady()

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Strips FTS5 special characters from a user-supplied query string so that
     * it can be used safely as an FTS5 MATCH argument.
     *
     * Removed characters: `"  *  ^  ~  -  +  :  (  )  |  &  !`
     * Returns a blank string if nothing remains — callers treat blank as "skip FTS".
     */
    private fun sanitizeFtsQuery(raw: String): String =
        raw.replace(Regex("""["*^~\-+:()|&!]"""), " ").trim()

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
