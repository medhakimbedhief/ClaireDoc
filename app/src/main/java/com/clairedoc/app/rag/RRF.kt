package com.clairedoc.app.rag

import com.clairedoc.app.data.db.FtsResult

/**
 * Reciprocal Rank Fusion — combines a vector-ranked list and a BM25-ranked list
 * into a single fused ranking.
 *
 * Formula: for each item at rank r (0-indexed) in a list, its RRF contribution is
 *   score += 1 / (K + r + 1)
 * where K = 60 (standard constant; dampens the effect of top-ranked items).
 *
 * Items appearing in **both** lists accumulate scores from both, so they float to
 * the top of the fused ranking — giving the "best of both worlds" for keyword-exact
 * and semantically-similar results.
 *
 * The function is pure (no I/O, no Android dependencies) — unit-testable on the JVM.
 */
object RRF {

    private const val K = 60.0

    /**
     * Fuses [vectorRanked] and [bm25Ranked] into a single deduplicated ranking.
     *
     * @param vectorRanked Chunks ranked by cosine similarity, highest score first.
     * @param bm25Ranked   FTS5 result rows, ordered ascending by [FtsResult.rank]
     *                     (most negative BM25 score = best match first).
     * @param chunkById    Lookup map for [DocumentChunk]s by their ObjectBox id — used
     *                     to resolve BM25 results whose chunks are not present in
     *                     [vectorRanked]. Pass only the supplemental chunks here; the
     *                     function already looks up vector chunks from [vectorRanked].
     * @return Fused list sorted by RRF score descending, deduplicated by chunk id.
     */
    fun fuse(
        vectorRanked: List<RankedChunk>,
        bm25Ranked: List<FtsResult>,
        chunkById: Map<Long, DocumentChunk>
    ): List<RankedChunk> {
        val rrfScores = mutableMapOf<Long, Double>()

        // Accumulate vector contribution
        vectorRanked.forEachIndexed { rank, rc ->
            rrfScores.merge(rc.chunk.id, 1.0 / (K + rank + 1), Double::plus)
        }

        // Accumulate BM25 contribution
        bm25Ranked.forEachIndexed { rank, fts ->
            rrfScores.merge(fts.chunkId, 1.0 / (K + rank + 1), Double::plus)
        }

        // Build a complete chunk lookup: vector results take precedence, extras fill the gaps
        val vectorChunks: Map<Long, DocumentChunk> = vectorRanked.associate { it.chunk.id to it.chunk }

        return rrfScores.entries
            .sortedByDescending { it.value }
            .mapNotNull { (id, score) ->
                val chunk = vectorChunks[id] ?: chunkById[id] ?: return@mapNotNull null
                RankedChunk(chunk, score)
            }
    }
}
