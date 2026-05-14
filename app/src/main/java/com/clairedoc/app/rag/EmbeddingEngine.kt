package com.clairedoc.app.rag

import android.content.Context
import android.util.Log
import com.clairedoc.app.engine.ModelDownloadManager
import org.tensorflow.lite.Interpreter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

private const val TAG = "EmbeddingEngine"

/**
 * Sequence length the model was exported with.
 * EmbeddingGemma-300M uses seq_len = 512 (file: embeddinggemma-300M_seq512_…).
 */
private const val SEQ_LEN = 512

/** Full output dimensionality of EmbeddingGemma-300M. */
private const val FULL_DIMS = 768

/** Matryoshka truncation: first 256 dims are a valid sub-embedding. */
private const val EMBEDDING_DIMS = 256

/**
 * Prompt prefixes required by EmbeddingGemma-300M.
 * The model was trained with these prefixes — omitting them degrades quality.
 */
private const val CHUNK_PREFIX = "title: none | text: "
private const val QUERY_PREFIX = "task: search result | query: "

/**
 * Neural embedding engine backed by EmbeddingGemma-300M via LiteRT.
 *
 * The TFLite model is loaded lazily on the first [embed] / [embedQuery] call so
 * that app startup is not blocked.  Both public methods are synchronous and MUST
 * be called from a non-main thread (IO dispatcher).
 *
 * Output: 256-dim L2-normalised FloatArray (Matryoshka truncation of the 768-dim
 * pooled output).
 */
@Singleton
class EmbeddingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenizer: GemmaTokenizer,
    private val modelDownloadManager: ModelDownloadManager
) {
    @Volatile private var interpreter: Interpreter? = null

    /** True when the TFLite model file is present and large enough to be valid. */
    fun isModelReady(): Boolean = modelDownloadManager.checkEmbedderExists()

    // ── Public API ────────────────────────────────────────────────────────────

    /** Embeds a document passage for indexing. */
    fun embed(text: String): FloatArray = executeInference(CHUNK_PREFIX + text)

    /** Embeds a search query for retrieval (symmetric dot-product space). */
    fun embedQuery(query: String): FloatArray = executeInference(QUERY_PREFIX + query)

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun executeInference(text: String): FloatArray {
        val interp = interpreter ?: initInterpreter()
        if (!tokenizer.isLoaded) tokenizer.encode("warmup")   // load vocab on first call

        // Tokenize → int32 input tensor [1, SEQ_LEN]
        val tokenIds = tokenizer.encode(text, maxLength = SEQ_LEN)
        val inputBuffer = ByteBuffer
            .allocateDirect(1 * SEQ_LEN * Int.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        for (id in tokenIds) inputBuffer.putInt(id)
        inputBuffer.rewind()

        // Output buffer [1, FULL_DIMS] float32
        val outputBuffer = ByteBuffer
            .allocateDirect(1 * FULL_DIMS * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())

        // Handle models with 1 input (input_ids only) or 3 inputs (+attention_mask, +token_type_ids)
        val inputCount = interp.getInputTensorCount()
        if (inputCount == 1) {
            interp.run(inputBuffer as Any, outputBuffer as Any)
        } else {
            // Build attention mask: 1 for real tokens, 0 for PAD
            val maskBuffer = ByteBuffer
                .allocateDirect(1 * SEQ_LEN * Int.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
            for (id in tokenIds) maskBuffer.putInt(if (id != 0) 1 else 0)
            maskBuffer.rewind()

            val inputs = arrayOfNulls<Any>(inputCount)
            inputs[0] = inputBuffer
            if (inputCount >= 2) inputs[1] = maskBuffer
            if (inputCount >= 3) {
                // token_type_ids — all zeros for single-sequence input
                val typeBuffer = ByteBuffer
                    .allocateDirect(1 * SEQ_LEN * Int.SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                repeat(SEQ_LEN) { typeBuffer.putInt(0) }
                typeBuffer.rewind()
                inputs[2] = typeBuffer
            }
            @Suppress("UNCHECKED_CAST")
            val outputs: Map<Int, Any> = mapOf(0 to (outputBuffer as Any))
            interp.runForMultipleInputsOutputs(inputs, outputs)
        }

        // Read output
        outputBuffer.rewind()
        val full = FloatArray(FULL_DIMS) { outputBuffer.float }

        return truncateAndNormalize(full)
    }

    /** Lazy interpreter init — called at most once per process. */
    @Synchronized
    private fun initInterpreter(): Interpreter {
        interpreter?.let { return it }

        val modelFile = File(
            context.getExternalFilesDir("models") ?: context.filesDir,
            "embedder/embeddinggemma-300m.tflite"
        )
        if (!modelFile.exists()) {
            throw IllegalStateException("Embedder model not found at ${modelFile.absolutePath}")
        }

        Log.d(TAG, "Loading TFLite model from ${modelFile.absolutePath}")
        val options = Interpreter.Options().apply { setNumThreads(2) }
        return Interpreter(modelFile, options).also {
            interpreter = it
            Log.d(TAG, "TFLite interpreter ready. Inputs: ${it.getInputTensorCount()}")
        }
    }

    /** Matryoshka truncation: take first [EMBEDDING_DIMS] floats then L2-normalise. */
    private fun truncateAndNormalize(full: FloatArray): FloatArray {
        val trunc = full.copyOf(EMBEDDING_DIMS)
        var norm = 0f
        for (x in trunc) norm += x * x
        norm = sqrt(norm)
        if (norm < 1e-10f) return trunc
        return FloatArray(EMBEDDING_DIMS) { trunc[it] / norm }
    }
}
