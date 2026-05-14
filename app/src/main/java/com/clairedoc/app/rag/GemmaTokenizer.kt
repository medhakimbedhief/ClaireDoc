package com.clairedoc.app.rag

import android.content.Context
import android.util.Log
import com.google.gson.stream.JsonReader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GemmaTokenizer"

private const val PAD_TOKEN_ID  = 0
private const val EOS_TOKEN_ID  = 1
private const val BOS_TOKEN_ID  = 2
private const val UNK_TOKEN_ID  = 3

/** U+2581 — SentencePiece word-boundary marker prepended to each word. */
private const val WORD_BOUNDARY = "▁"

/**
 * Upper bound on a single subword token length in the Gemma 256k vocabulary.
 * Scanning from MAX_TOKEN_LEN down to 1 keeps MaxMatch O(n * MAX_TOKEN_LEN).
 */
private const val MAX_TOKEN_LEN = 20

private const val ASSET_NAME = "tokenizer_embedding_300m.json"

/**
 * Pure-Kotlin greedy-MaxMatch BPE tokenizer for EmbeddingGemma-300M.
 *
 * Algorithm:
 *  1. Split text on whitespace; prepend ▁ to each word (SentencePiece convention).
 *  2. For every word, scan left-to-right: try the longest substring [0..len) that
 *     exists in the vocabulary; emit its ID.
 *  3. If no match of length ≥ 1 exists, fall back to the byte token <0xNN>.
 *  4. Prepend BOS (2), append EOS (1); truncate to [maxLength]; pad with PAD (0).
 *
 * The tokenizer JSON is parsed lazily on the first [encode] call using Gson's
 * streaming [JsonReader], which tolerates the duplicate case-variant keys that
 * are present in the Gemma vocabulary (e.g. both "▁S" and "▁s").
 *
 * Memory: the full 256 k-token vocabulary occupies ~50 MB once loaded.
 */
@Singleton
class GemmaTokenizer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @Volatile private var vocab: HashMap<String, Int>? = null

    val isLoaded: Boolean get() = vocab != null

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Encodes [text] into a token-ID array of exactly [maxLength] ints.
     * Layout: [BOS] [tokens…] [EOS] [PAD…]
     *
     * Blocking — must be called from a non-main thread (e.g. inside
     * `withContext(Dispatchers.IO)`).
     */
    fun encode(text: String, maxLength: Int = 512): IntArray {
        val v = vocab ?: loadVocab()
        val ids = ArrayList<Int>(maxLength)

        ids.add(BOS_TOKEN_ID)

        // Pre-tokenize: split on whitespace, add ▁ prefix
        val words = text.trim().split(Regex("\\s+"))
        outer@ for ((wordIdx, raw) in words.withIndex()) {
            val word = if (wordIdx == 0) WORD_BOUNDARY + raw else WORD_BOUNDARY + raw
            var pos = 0
            while (pos < word.length) {
                if (ids.size >= maxLength - 1) break@outer   // reserve slot for EOS
                var matched = false
                val maxLen = minOf(word.length - pos, MAX_TOKEN_LEN)
                for (len in maxLen downTo 1) {
                    val sub = word.substring(pos, pos + len)
                    val id = v[sub]
                    if (id != null) {
                        ids.add(id)
                        pos += len
                        matched = true
                        break
                    }
                }
                if (!matched) {
                    // Byte fallback: encode the char as <0xNN>
                    val c = word[pos]
                    val byteToken = "<0x${c.code.and(0xFF).toString(16).uppercase().padStart(2, '0')}>"
                    ids.add(v[byteToken] ?: UNK_TOKEN_ID)
                    pos++
                }
            }
        }

        if (ids.size < maxLength) ids.add(EOS_TOKEN_ID)

        // Truncate and pad to exactly maxLength
        val result = IntArray(maxLength) { PAD_TOKEN_ID }
        for (i in 0 until minOf(ids.size, maxLength)) result[i] = ids[i]
        return result
    }

    // ── Vocab loading ─────────────────────────────────────────────────────────

    /**
     * Parses the `model.vocab` section of the HuggingFace fast-tokenizer JSON
     * using a streaming [JsonReader].
     *
     * Streaming avoids two failure modes:
     *  - The 33 MB file is too large to hold in a single String on low-memory devices.
     *  - The vocab contains duplicate case-variant keys (`▁S` vs `▁s`) that crash
     *    non-streaming parsers (including Gson `fromJson`, PowerShell, Android JSON).
     *    Streaming simply overwrites the earlier entry (last value wins — fine).
     */
    private fun loadVocab(): HashMap<String, Int> {
        Log.d(TAG, "Loading tokenizer vocabulary from assets…")
        val t0 = System.currentTimeMillis()

        val map = HashMap<String, Int>(320_000)   // pre-size above 256k to minimise rehash

        context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
            val jr = JsonReader(reader)
            jr.isLenient = true
            jr.beginObject()                                     // top-level {

            while (jr.hasNext()) {
                val topKey = jr.nextName()
                if (topKey == "model") {
                    jr.beginObject()                             // "model": {
                    while (jr.hasNext()) {
                        val modelKey = jr.nextName()
                        if (modelKey == "vocab") {
                            jr.beginObject()                     // "vocab": {
                            while (jr.hasNext()) {
                                val token = jr.nextName()        // "▁hello"
                                val id    = jr.nextInt()         // 12345
                                map[token] = id
                            }
                            jr.endObject()
                        } else {
                            jr.skipValue()
                        }
                    }
                    jr.endObject()
                } else {
                    jr.skipValue()
                }
            }
            // We don't close the top-level object — we bail early once vocab is found.
        }

        val elapsed = System.currentTimeMillis() - t0
        Log.d(TAG, "Vocabulary loaded: ${map.size} tokens in ${elapsed} ms")

        vocab = map
        return map
    }
}
