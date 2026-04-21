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
