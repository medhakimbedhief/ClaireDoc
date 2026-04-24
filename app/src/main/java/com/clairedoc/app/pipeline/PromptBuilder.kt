package com.clairedoc.app.pipeline

import javax.inject.Inject

/**
 * Holds the exact system prompt for Gemma 4 E2B document analysis.
 * Do NOT paraphrase or modify [SYSTEM_PROMPT] — the model was evaluated
 * against this exact wording and schema.
 */
class PromptBuilder @Inject constructor() {

    val systemPrompt: String = SYSTEM_PROMPT

    /** Vision path: image is already attached as Content.ImageFile. */
    fun buildVisionUserMessage(): String =
        "Analyze this document image and respond with the JSON schema."

    /**
     * Text-only fallback: used when the .litertlm model does not include
     * a vision encoder and runtime throws on multimodal input.
     */
    fun buildTextFallbackUserMessage(extractedText: String): String =
        "Analyze this document text and respond with the JSON schema:\n\n$extractedText"

    /**
     * System prompt for follow-up Q&A on an already-analysed document.
     *
     * IMPORTANT: this prompt must NOT include [SYSTEM_PROMPT] — that prompt
     * instructs the model to output JSON only, which is the wrong behaviour here.
     * This is a plain-language conversational system prompt.
     *
     * [analysisJson] is the Gson serialisation of the [DocumentResult] produced
     * during the initial scan.  The model uses it as ground truth.
     */
    fun buildFollowUpSystemPrompt(analysisJson: String): String = buildString {
        append("You are a helpful assistant that explains official documents to ordinary people.\n")
        append("You have already analysed a document. Here is what you found:\n\n")
        append(analysisJson)
        append("\n\n")
        append("The user has a follow-up question about this document.\n")
        append("Answer in plain, simple language — like you are explaining to a 12-year-old.\n")
        append("Do NOT output JSON. Do NOT start with the analysis again.\n")
        append("Give a short, direct answer in 1 to 4 sentences.")
    }

    /** User turn for a follow-up question. */
    fun buildFollowUpUserMessage(question: String): String = question

    companion object {
        // Keep this as a top-level companion constant so tests can assert
        // the exact string without instantiating the class.
        const val SYSTEM_PROMPT = """You are a document assistant helping people understand official documents.
Analyze the provided document image.
Respond ONLY with a valid JSON object. No explanation, no markdown fences, no preamble.

Use this exact schema:
{
  "documentType": "BILL|CONTRACT|LEGAL_NOTICE|MEDICAL|OTHER",
  "summary": ["plain bullet 1", "plain bullet 2", "plain bullet 3"],
  "actions": [
    {"description": "action text", "deadline": "YYYY-MM-DD or null", "urgency": "RED|YELLOW|GREEN"}
  ],
  "risks": ["risk description"],
  "urgencyLevel": "RED|YELLOW|GREEN"
}

Rules:
- Use plain language a 12-year-old can understand
- Extract all amounts (€, $, £) and dates precisely
- deadline field: ISO date string if found, null if not
- urgencyLevel RED = legal deadline / eviction / visa risk
- urgencyLevel YELLOW = action needed, no immediate deadline
- urgencyLevel GREEN = informational only"""
    }
}
