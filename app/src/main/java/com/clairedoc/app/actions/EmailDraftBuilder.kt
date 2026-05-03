package com.clairedoc.app.actions

import com.clairedoc.app.data.model.DocumentResult
import com.clairedoc.app.engine.EngineState
import com.clairedoc.app.engine.LiteRTEngine
import com.clairedoc.app.pipeline.PromptBuilder
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates a professional email draft using on-device inference.
 *
 * Text-only path: no image is attached because the document context is
 * already embedded in the system prompt as the analysis JSON.
 *
 * All dependencies are provided by Hilt automatically via
 * [@Singleton @Inject constructor] — no [AppModule] entry needed.
 */
@Singleton
class EmailDraftBuilder @Inject constructor(
    private val engine: LiteRTEngine,
    private val promptBuilder: PromptBuilder,
    private val gson: Gson
) {
    /** Internal DTO — only used for parsing the model's JSON response. */
    private data class EmailDraftDto(val subject: String?, val body: String?)

    suspend fun generateDraft(
        documentResult: DocumentResult,
        recipientEmail: String,
        userIntent: String
    ): Result<EmailDraft> = runCatching {
        // Idempotent — no-op if engine is already Ready
        engine.initialize()
        val state = engine.state.value
        if (state is EngineState.Error) {
            error("Engine not ready: ${state.message}")
        }

        val analysisJson = gson.toJson(documentResult)
        val language = documentResult.detectedLanguage ?: "English"
        val systemPrompt = promptBuilder.buildEmailDraftPrompt(
            analysisJson = analysisJson,
            userIntent = userIntent,
            detectedLanguage = language
        )

        // Text-only inference — no image needed for drafting
        val sb = StringBuilder()
        engine.generateResponse(
            imageFile = null,
            systemPrompt = systemPrompt,
            userPrompt = "Write the email draft now."
        ).collect { token -> sb.append(token) }

        val dto = gson.fromJson(extractJson(sb.toString()), EmailDraftDto::class.java)
            ?: error("Could not parse email draft from model output")

        EmailDraft(
            to = recipientEmail,
            subject = dto.subject?.takeIf { it.isNotBlank() } ?: "Re: Your document",
            body = dto.body?.takeIf { it.isNotBlank() } ?: error("Empty email body")
        )
    }

    // Two-step defensive JSON extraction — mirrors JsonParser without coupling
    // the actions package to the pipeline package.
    private val fencePattern = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```")
    private val objectPattern = Regex("\\{[\\s\\S]*\\}")

    private fun extractJson(raw: String): String {
        fencePattern.find(raw)?.let { return it.groupValues[1].trim() }
        objectPattern.find(raw)?.let { return it.value }
        return raw
    }
}
