package com.clairedoc.app.rag

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [Chunker].
 *
 * No Android framework dependencies — runs directly on the JVM via `./gradlew test`.
 * Uses a ~1000-word German bureaucratic text (50 × 20-word sentence) as test data.
 */
class ChunkerTest {

    /**
     * Generates a ~1000-word German bureaucratic text.
     * A single sentence is repeated 50 times; each sentence is ~20 words.
     * Using repeated identical sentences ensures the overlap test has clear
     * common tokens between consecutive chunks.
     */
    private fun buildTestText(): String {
        val sentence =
            "Hiermit teilen wir Ihnen mit, dass Ihr Antrag auf Gewährung einer " +
            "Unterstützungsleistung gemäß Paragraf siebzehn des Sozialgesetzbuches " +
            "eingegangen ist und bearbeitet wird."
        // ~20 words × 50 repetitions ≈ 1 000 words
        return (1..50).joinToString(" ") { sentence }
    }

    @Test
    fun `multiple chunks are produced from a long text`() {
        val chunks = Chunker.chunk(buildTestText())
        assertTrue(
            "Expected more than 1 chunk for ~1000-word input, got ${chunks.size}",
            chunks.size > 1
        )
    }

    @Test
    fun `each chunk stays under 350 words`() {
        val chunks = Chunker.chunk(buildTestText())
        chunks.forEachIndexed { i, chunk ->
            val wordCount = chunk.split(Regex("\\s+")).count { it.isNotEmpty() }
            assertTrue(
                "Chunk $i contains $wordCount words, which exceeds the 350-word limit",
                wordCount < 350
            )
        }
    }

    @Test
    fun `consecutive chunks share overlapping words`() {
        val chunks = Chunker.chunk(buildTestText())
        assertTrue(
            "Need at least 2 chunks to verify overlap, got ${chunks.size}",
            chunks.size >= 2
        )

        // Compare last 30 words of chunk[0] against first 30 words of chunk[1]
        val tailWords = chunks[0]
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .takeLast(30)
            .toSet()

        val headWords = chunks[1]
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(30)
            .toSet()

        val intersection = tailWords.intersect(headWords)
        assertTrue(
            "Expected overlapping words between chunk[0] tail and chunk[1] head; " +
            "intersection was empty.\n  tail sample: ${tailWords.take(5)}\n" +
            "  head sample: ${headWords.take(5)}",
            intersection.isNotEmpty()
        )
    }
}
