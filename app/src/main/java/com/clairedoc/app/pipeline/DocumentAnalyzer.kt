package com.clairedoc.app.pipeline

import android.content.Context
import android.net.Uri
import android.util.Log
import com.clairedoc.app.data.model.DocumentResult
import com.clairedoc.app.data.model.toDomain
import com.clairedoc.app.engine.EngineState
import com.clairedoc.app.engine.LiteRTEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

private const val TAG = "ClaireDoc_AI"

class DocumentAnalyzer @Inject constructor(
    private val engine: LiteRTEngine,
    private val promptBuilder: PromptBuilder,
    private val jsonParser: JsonParser
) {
    sealed class AnalysisResult {
        data class Success(val result: DocumentResult) : AnalysisResult()
        data class Failure(val message: String, val cause: Throwable? = null) : AnalysisResult()
    }

    /**
     * Runs the full analysis pipeline for [imageUri]:
     *  1. Copy content:// URI → temp JPEG file (LiteRT needs an absolute path)
     *  2. Stream tokens from Gemma via [LiteRTEngine.generateResponse]
     *  3. Log the full raw JSON to Logcat (tag "ClaireDoc_AI")
     *  4. Defensively parse JSON → [DocumentResult]
     *
     * This function is CPU/IO-intensive; always call from a non-main coroutine.
     */
    suspend fun analyze(imageUri: Uri, context: Context): AnalysisResult {
        // Step 0 — ensure engine is ready (no-op if already initialised)
        // Handles the case where the user opens the app with model already present
        // and the Download screen (which normally calls initialize) is skipped.
        if (engine.state.value !is EngineState.Ready) {
            engine.initialize()
        }
        if (engine.state.value is EngineState.Error) {
            val err = engine.state.value as EngineState.Error
            return AnalysisResult.Failure("Engine failed to initialise: ${err.message}", err.cause)
        }

        // Step 1 — materialise content:// URI into a real file
        val imageFile = runCatching { copyUriToTempFile(imageUri, context) }
            .getOrElse { ex ->
                return AnalysisResult.Failure("Could not read scanned image: ${ex.message}", ex)
            }

        // Step 2 — run LiteRT inference (vision-capable path)
        val rawResponse = runCatching {
            collectResponse(imageFile, visionEnabled = true)
        }.getOrElse { visionEx ->
            // Vision encoder may be absent in the .litertlm weights —
            // fall back to text-only mode if the runtime throws.
            Log.w(TAG, "Vision inference failed (${visionEx.message}); retrying text-only…")
            runCatching {
                collectResponse(imageFile = null, visionEnabled = false)
            }.getOrElse { ex ->
                return AnalysisResult.Failure("Inference failed: ${ex.message}", ex)
            }
        }

        // Step 3 — log raw output (required by spec)
        Log.d(TAG, "=== RAW MODEL RESPONSE ===\n$rawResponse\n=========================")

        // Step 4 — parse + map to domain
        val dto = jsonParser.parse(rawResponse)
            ?: return AnalysisResult.Failure(
                "Model returned unparseable output. Raw response logged above."
            )

        return AnalysisResult.Success(dto.toDomain())
    }

    private suspend fun collectResponse(imageFile: File?, visionEnabled: Boolean): String {
        // buildString uses a non-suspend lambda so we collect into a plain StringBuilder.
        val sb = StringBuilder()
        engine.generateResponse(
            imageFile = if (visionEnabled) imageFile else null,
            systemPrompt = promptBuilder.systemPrompt,
            userPrompt = if (visionEnabled)
                promptBuilder.buildVisionUserMessage()
            else
                promptBuilder.buildTextFallbackUserMessage(
                    imageFile?.let { extractTextFromImage(it) } ?: ""
                )
        ).collect { token -> sb.append(token) }
        return sb.toString()
    }

    /**
     * Copies a [Uri] (including content:// scheme from ML Kit) to a temp file
     * in [Context.cacheDir] so LiteRT-LM can open it via absolute path.
     */
    private fun copyUriToTempFile(uri: Uri, context: Context): File {
        val tmp = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        return tmp
    }

    /**
     * Lightweight text extraction from a JPEG for the text-only fallback path.
     * Returns the file path as a placeholder; the model can still process it
     * if the caller constructs the prompt appropriately.
     *
     * A full ML Kit Text Recognition integration can be added here later if
     * the vision encoder proves consistently unavailable.
     */
    private fun extractTextFromImage(imageFile: File): String =
        "[Image file: ${imageFile.absolutePath} — vision encoder unavailable, " +
                "please describe what you see in this document]"
}
