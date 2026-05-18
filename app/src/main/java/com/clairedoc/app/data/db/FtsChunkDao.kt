package com.clairedoc.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.SkipQueryVerification

/**
 * Result row returned by the FTS5 BM25 search query.
 * [rank] is the raw BM25 score: more negative = more relevant.
 * In practice we only use the list position for RRF, not the raw value.
 */
data class FtsResult(
    @ColumnInfo(name = "chunk_id") val chunkId: Long,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val rank: Double
)

/**
 * DAO for the [chunk_fts] FTS5 virtual table.
 *
 * The table is created by [MIGRATION_6_7] — it is NOT a Room @Entity so Room does
 * not generate schema-validation code for it. All queries use raw SQL.
 *
 * [@SkipQueryVerification] suppresses KSP compile-time "no such table" errors:
 * Room's annotation processor only knows about @Entity tables and cannot see FTS5
 * virtual tables that are created by migrations. The queries are correct at runtime.
 *
 * Note: the FTS5 table is a regular (not contentless) table so that rows can be
 * deleted with standard DELETE statements. Text content is stored inline in the
 * FTS5 B-tree; the duplicate storage cost relative to ObjectBox is negligible.
 */
@Dao
@SkipQueryVerification
interface FtsChunkDao {

    /**
     * Inserts a chunk into the FTS5 index.
     * Call this right after ObjectBox assigns an ID to the chunk.
     */
    @Query("INSERT INTO chunk_fts(chunk_id, session_id, text) VALUES (:chunkId, :sessionId, :text)")
    suspend fun insertFts(chunkId: Long, sessionId: String, text: String)

    /**
     * BM25 search across ALL sessions.
     * Rows are ordered by [rank] ascending (most negative = best match first).
     * [query] must already be sanitized — no FTS5 special characters.
     */
    @Query("SELECT chunk_id, session_id, rank FROM chunk_fts WHERE text MATCH :query ORDER BY rank LIMIT :limit")
    suspend fun searchBm25(query: String, limit: Int): List<FtsResult>

    /**
     * BM25 search scoped to a single [sessionId].
     * Used by [ChunkRepository.retrieve] for per-document Q&A.
     */
    @Query("SELECT chunk_id, session_id, rank FROM chunk_fts WHERE text MATCH :query AND session_id = :sessionId ORDER BY rank LIMIT :limit")
    suspend fun searchBm25ForSession(query: String, sessionId: String, limit: Int): List<FtsResult>

    /**
     * Removes all FTS5 rows for [sessionId].
     * Must be called in sync with [ChunkRepository.deleteChunksForSession].
     */
    @Query("DELETE FROM chunk_fts WHERE session_id = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)
}
