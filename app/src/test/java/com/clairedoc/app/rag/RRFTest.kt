package com.clairedoc.app.rag

import com.clairedoc.app.data.db.FtsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [RRF].
 *
 * No Android or ObjectBox runtime required — [DocumentChunk] is a plain Kotlin data class
 * whose @Entity annotation has no effect on JVM instantiation.
 *
 * RRF score at 0-indexed rank r: 1 / (60 + r + 1)
 * Items appearing in both lists accumulate from both, so they float to the top.
 */
class RRFTest {

    private fun chunk(id: Long) = DocumentChunk(
        id            = id,
        sessionId     = "session",
        documentTitle = "Doc",
        chunkIndex    = id.toInt(),
        totalChunks   = 10,
        text          = "text $id",
        embedding     = null
    )

    private fun ftsResult(chunkId: Long, rank: Int) =
        FtsResult(chunkId = chunkId, sessionId = "session", rank = -rank.toDouble())

    // ── Test 1 ─────────────────────────────────────────────────────────────────

    /**
     * IDs 1–5 from vector (positions 0–4), IDs 3–7 from BM25 (positions 0–4).
     * Overlapping IDs 3, 4, 5 each accumulate from both lists — expect them in top 3.
     *
     * Manual RRF scores (K=60):
     *   ID 1 → 1/61  ≈ 0.01639   (vector pos 0 only)
     *   ID 2 → 1/62  ≈ 0.01613   (vector pos 1 only)
     *   ID 3 → 1/63 + 1/61 ≈ 0.03226  (vector pos 2 + BM25 pos 0)
     *   ID 4 → 1/64 + 1/62 ≈ 0.03176  (vector pos 3 + BM25 pos 1)
     *   ID 5 → 1/65 + 1/63 ≈ 0.03125  (vector pos 4 + BM25 pos 2)
     *   ID 6 → 1/64  ≈ 0.01563   (BM25 pos 3 only)
     *   ID 7 → 1/65  ≈ 0.01538   (BM25 pos 4 only)
     * Expected order: 3, 4, 5, 1, 2, 6, 7
     */
    @Test
    fun `overlapping chunks score higher than non-overlapping`() {
        val vectorRanked = (1..5).map { id ->
            RankedChunk(chunk(id.toLong()), score = 1.0 - id * 0.1)
        }
        val bm25Ranked = (0..4).map { i ->
            ftsResult(chunkId = (3 + i).toLong(), rank = i + 1)
        }
        val extraChunks = mapOf(6L to chunk(6L), 7L to chunk(7L))

        val result = RRF.fuse(vectorRanked, bm25Ranked, extraChunks)

        val topThreeIds = result.take(3).map { it.chunk.id }.toSet()
        assertTrue(
            "Expected overlapping IDs {3, 4, 5} in top 3, got $topThreeIds",
            topThreeIds.containsAll(listOf(3L, 4L, 5L))
        )
    }

    // ── Test 2 ─────────────────────────────────────────────────────────────────

    /**
     * 5 vector + 5 non-overlapping BM25 → 10 distinct results; take(5).size == 5.
     */
    @Test
    fun `result contains at least topK items when inputs are distinct`() {
        val vectorRanked = (1..5).map { id ->
            RankedChunk(chunk(id.toLong()), score = 1.0 - id * 0.1)
        }
        val bm25Ranked = (1..5).map { i ->
            ftsResult(chunkId = (5 + i).toLong(), rank = i)
        }
        val extraChunks = (6..10).associate { id -> id.toLong() to chunk(id.toLong()) }

        val result = RRF.fuse(vectorRanked, bm25Ranked, extraChunks)

        assertEquals("Expected 10 distinct results", 10, result.size)
        assertEquals("take(5) must return exactly 5", 5, result.take(5).size)
    }

    // ── Test 3 ─────────────────────────────────────────────────────────────────

    /**
     * When BM25 list is empty, fused result should preserve vector ranking order.
     */
    @Test
    fun `empty BM25 list preserves vector order`() {
        val vectorRanked = (1..3).map { id ->
            RankedChunk(chunk(id.toLong()), score = 1.0 - id * 0.1)
        }

        val result = RRF.fuse(vectorRanked, emptyList(), emptyMap())

        assertEquals(
            "With BM25 empty, order should follow vector ranking",
            listOf(1L, 2L, 3L),
            result.map { it.chunk.id }
        )
    }
}
