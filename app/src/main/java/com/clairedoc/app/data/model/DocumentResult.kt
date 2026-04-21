package com.clairedoc.app.data.model

/**
 * Immutable output contract. Never change this schema — downstream UI and TTS
 * depend on these exact field names and types.
 */
data class DocumentResult(
    val documentType: String,       // "BILL|CONTRACT|LEGAL_NOTICE|MEDICAL|OTHER"
    val summary: List<String>,      // 3-4 plain language bullets
    val actions: List<ActionItem>,  // extracted obligations with optional deadlines
    val risks: List<String>,        // warnings
    val urgencyLevel: UrgencyLevel  // RED | YELLOW | GREEN
)
