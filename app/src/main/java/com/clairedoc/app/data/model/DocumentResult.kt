package com.clairedoc.app.data.model

// ─── Enrichment types ─────────────────────────────────────────────────────────

data class SenderInfo(
    val name: String?,
    val department: String?
)

enum class ContactType { EMAIL, PHONE, ADDRESS, WEBSITE }

data class ContactInfo(
    val type: ContactType,
    val value: String,
    val label: String          // e.g. "Customer Service", "Head Office"
)

/**
 * Model's self-reported confidence in its own analysis.
 * HIGH  = document is clear and fully readable.
 * MEDIUM = some parts unclear or partially occluded.
 * LOW   = poor image quality or document type not recognised.
 */
enum class Confidence { HIGH, MEDIUM, LOW }

data class GlossaryTerm(
    val term: String,
    val plainExplanation: String  // one plain-language sentence
)

// ─── Core output contract ─────────────────────────────────────────────────────

/**
 * Immutable output contract for Gemma 4 E2B document analysis.
 *
 * Fields [documentType], [summary], [actions], [risks] and [urgencyLevel] are
 * the original v1 contract — never remove or rename them.
 *
 * Fields from [sender] onwards are v2 enrichments; they carry sensible defaults
 * so that sessions stored before v2 can still be reconstructed from Room without
 * a database migration.
 */
data class DocumentResult(
    val documentType: String,           // see VALID_DOCUMENT_TYPES in DocumentResultDto
    val summary: List<String>,          // 2–5 plain-language bullets
    val actions: List<ActionItem>,      // extracted obligations, each with optional deadline
    val risks: List<String>,            // warnings / red-flag sentences
    val urgencyLevel: UrgencyLevel,     // RED | YELLOW | GREEN

    // v2 enrichment fields — all nullable so old stored JSON deserialised by Gson
    // (which bypasses constructor defaults via Unsafe) never causes NPE.
    // Consumers should use: contacts.orEmpty(), confidence ?: Confidence.MEDIUM, etc.
    val sender: SenderInfo? = null,
    val contacts: List<ContactInfo>? = null,
    val detectedLanguage: String? = null,   // ISO 639-1 code, e.g. "en", "fr"
    val confidence: Confidence? = null,
    val glossaryTerms: List<GlossaryTerm>? = null
)
