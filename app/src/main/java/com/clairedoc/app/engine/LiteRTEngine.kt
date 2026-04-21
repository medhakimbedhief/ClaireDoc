package com.clairedoc.app.engine

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "ClaireDoc_Engine"
private const val MODEL_DIR = "models"
private const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
// Model is 2.58 GB; guard against a half-downloaded file sitting on disk.
private const val MIN_MODEL_SIZE_BYTES = 100_000_000L

class LiteRTEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : Closeable {

    private var engine: Engine? = null
    private var isGpuBackend: Boolean = false

    private val _state = MutableStateFlow<EngineState>(EngineState.Uninitialized)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    val modelFile: File
        get() = File(context.getExternalFilesDir(MODEL_DIR), MODEL_FILENAME)

    /**
     * Returns true only when the model file exists AND its size is above the
     * minimum threshold — guards against partial downloads.
     */
    val isModelPresent: Boolean
        get() = modelFile.exists() && modelFile.length() >= MIN_MODEL_SIZE_BYTES

    /**
     * Initialises the LiteRT-LM engine on [Dispatchers.IO].
     * Attempts GPU (Adreno 642L) first, falls back to CPU on failure.
     * Safe to call multiple times — no-ops if already [EngineState.Ready].
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (_state.value is EngineState.Ready) return@withContext
        if (!isModelPresent) {
            _state.value = EngineState.Error("Model file not found at ${modelFile.absolutePath}")
            return@withContext
        }

        _state.value = EngineState.Initializing
        Log.d(TAG, "Attempting GPU backend initialisation…")

        // GPU attempt (Adreno 642L via OpenCL)
        val gpuResult = runCatching {
            val cfg = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.GPU(),
                visionBackend = Backend.GPU()
            )
            Engine(cfg).also { it.initialize() }
        }

        if (gpuResult.isSuccess) {
            engine = gpuResult.getOrThrow()
            isGpuBackend = true
            Log.d(TAG, "Engine ready — GPU backend")
            _state.value = EngineState.Ready(isGpu = true)
            return@withContext
        }

        Log.w(TAG, "GPU init failed (${gpuResult.exceptionOrNull()?.message}); retrying on CPU…")

        // CPU fallback
        val cpuResult = runCatching {
            val cfg = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                visionBackend = Backend.CPU()
            )
            Engine(cfg).also { it.initialize() }
        }

        if (cpuResult.isSuccess) {
            engine = cpuResult.getOrThrow()
            isGpuBackend = false
            Log.d(TAG, "Engine ready — CPU backend")
            _state.value = EngineState.Ready(isGpu = false)
            return@withContext
        }

        val ex = cpuResult.exceptionOrNull()
        Log.e(TAG, "CPU init also failed", ex)

        // Handle OOM explicitly — still an Error, but log it clearly.
        if (ex is OutOfMemoryError) {
            _state.value = EngineState.Error(
                "Out of memory loading model. Close other apps and retry.",
                ex
            )
        } else {
            _state.value = EngineState.Error(
                "Engine init failed: ${ex?.message}",
                ex
            )
        }
    }

    /**
     * Streams token strings from a single Gemma inference call.
     *
     * Each document analysis should create a fresh [Conversation] so the
     * KV-cache from a previous scan does not contaminate results.
     *
     * @param imageFile absolute-path JPEG file, or null for text-only fallback
     * @param systemPrompt exact system instruction — never modify in callers
     * @param userPrompt user turn content
     */
    fun generateResponse(
        imageFile: File?,
        systemPrompt: String,
        userPrompt: String
    ): Flow<String> = flow<String> {
        val e = requireNotNull(engine) { "Engine is not initialised. Call initialize() first." }
        check(_state.value is EngineState.Ready) { "Engine not ready, state=${_state.value}" }

        val convConfig = ConversationConfig(
            systemInstruction = Contents.of(systemPrompt)
        )

        e.createConversation(convConfig).use { conv ->
            val contentList = buildList {
                // Vision path: Gemma 4 E2B supports multimodal input.
                // If the .litertlm file was built without a vision encoder,
                // the runtime throws here — DocumentAnalyzer catches it and
                // retries in text-only mode.
                imageFile?.let { add(Content.ImageFile(it.absolutePath)) }
                add(Content.Text(userPrompt))
            }

            // sendMessageAsync returns Flow<Message>.
            // Each Message wraps a Contents whose inner list may contain
            // one or more Content.Text parts — join them into a single string
            // and emit only non-empty chunks.
            conv.sendMessageAsync(Contents.of(*contentList.toTypedArray()))
                .collect { message: Message ->
                    val chunk = message.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                    if (chunk.isNotEmpty()) emit(chunk)
                }
        }
    }.flowOn(Dispatchers.IO)

    override fun close() {
        engine?.close()
        engine = null
        isGpuBackend = false
        _state.value = EngineState.Uninitialized
        Log.d(TAG, "Engine released")
    }
}
