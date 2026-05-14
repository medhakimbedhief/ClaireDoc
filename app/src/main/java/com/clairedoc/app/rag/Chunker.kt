package com.clairedoc.app.rag

/**
 * Sliding-window sentence chunker for document text.
 *
 * Splits [text] into overlapping windows of ≤ [targetWords] words,
 * with consecutive chunks sharing ≈ [overlapWords] words at their boundary.
 * Pure Kotlin — no Android dependencies, safe to use in JVM unit tests.
 */
object Chunker {

    /** Matches whitespace that follows a sentence-ending punctuation character. */
    private val SENTENCE_BOUNDARY = Regex("(?<=[.!?;])\\s+")
    private val WHITESPACE = Regex("\\s+")

    /**
     * Chunks [text] into a list of overlapping passages.
     *
     * @param text         raw document text to split
     * @param targetWords  target word count per chunk (default 300)
     * @param overlapWords approximate word overlap between consecutive chunks (default 60)
     * @return list of passage strings; empty if [text] is blank
     */
    fun chunk(
        text: String,
        targetWords: Int = 300,
        overlapWords: Int = 60
    ): List<String> {
        if (text.isBlank()) return emptyList()

        // Split on sentence-ending punctuation; keep trailing fragments (no terminal punct)
        val sentences = SENTENCE_BOUNDARY.split(text.trim())
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (sentences.isEmpty()) return emptyList()

        // Pre-compute word count per sentence to avoid repeated splitting in the hot loop
        val wordCounts = sentences.map { s ->
            WHITESPACE.split(s.trim()).count { it.isNotEmpty() }
        }

        val chunks = mutableListOf<String>()
        var startIdx = 0

        while (startIdx < sentences.size) {
            // ── Build chunk body ──────────────────────────────────────────────
            var wordSum = 0
            var endIdx = startIdx  // exclusive end — sentences[startIdx..endIdx)

            while (endIdx < sentences.size) {
                val wc = wordCounts[endIdx]
                // Always include at least the first sentence (handles very long sentences).
                // Overflow check only fires once we have something in the chunk already.
                if (endIdx > startIdx && wordSum + wc > targetWords) break
                wordSum += wc
                endIdx++
            }
            // endIdx is now the EXCLUSIVE end of this chunk

            chunks.add(sentences.subList(startIdx, endIdx).joinToString(" "))

            if (endIdx >= sentences.size) break  // consumed all sentences

            // ── Find next startIdx (overlap) ──────────────────────────────────
            // Walk backwards from the end of the current chunk until we've
            // accumulated ≥ overlapWords; those sentences start the next chunk.
            var backWords = 0
            var nextStart = endIdx
            for (j in endIdx - 1 downTo startIdx) {
                backWords += wordCounts[j]
                nextStart = j
                if (backWords >= overlapWords) break
            }
            // Guarantee forward progress — startIdx must increase by at least 1
            startIdx = maxOf(nextStart, startIdx + 1)
        }

        return chunks
    }
}
