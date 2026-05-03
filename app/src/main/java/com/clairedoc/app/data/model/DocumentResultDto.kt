package com.clairedoc.app.data.model

/**
 * Gson-deserializable DTOs for the raw JSON emitted by Gemma 4 E2B.
 * All fields are nullable to survive malformed / partial model output.
 * Call [toDomain] to map into the validated [DocumentResult].
 */

// ─── DTO types ────────────────────────────────────────────────────────────────

data class ActionItemDto(
    val description: String?,
    val deadline: String?,
    val urgency: String?
)

data class SenderInfoDto(
    val name: String?,
    val department: String?
)

data class ContactInfoDto(
    val type: String?,
    val value: String?,
    val label: String?
)

data class GlossaryTermDto(
    val term: String?,
    val plainExplanation: String?
)

data class DocumentResultDto(
    val documentType: String?,
    val summary: List<String?>?,
    val actions: List<ActionItemDto?>?,
    val risks: List<String?>?,
    val urgencyLevel: String?,

    // v2 enrichment fields — all nullable; absent keys become null via Gson
    val sender: SenderInfoDto?,
    val contacts: List<ContactInfoDto?>?,
    val detectedLanguage: String?,
    val confidence: String?,
    val glossaryTerms: List<GlossaryTermDto?>?
)

// ─── Validation helpers ───────────────────────────────────────────────────────

private val VALID_DOCUMENT_TYPES = setOf(
    // v1
    "BILL", "CONTRACT", "LEGAL_NOTICE", "MEDICAL", "OTHER",
    // v2 additions
    "TAX", "INSURANCE", "BANK", "VISA_IMMIGRATION", "GOVERNMENT_NOTICE", "RENTAL"
)

private val ISO_DATE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")

private fun parseUrgency(raw: String?): UrgencyLevel =
    UrgencyLevel.entries.firstOrNull { it.name == raw?.uppercase().orEmpty() }
        ?: UrgencyLevel.YELLOW

private fun parseConfidence(raw: String?): Confidence =
    Confidence.entries.firstOrNull { it.name == raw?.uppercase().orEmpty() }
        ?: Confidence.MEDIUM

private fun parseContactType(raw: String?): ContactType? =
    ContactType.entries.firstOrNull { it.name == raw?.uppercase().orEmpty() }

// ─── Domain mapper ────────────────────────────────────────────────────────────

fun DocumentResultDto.toDomain(): DocumentResult {
    val docType = documentType?.uppercase().orEmpty()
        .let { if (it in VALID_DOCUMENT_TYPES) it else "OTHER" }

    return DocumentResult(
        documentType = docType,
        summary = summary
            ?.filterNotNull()
            ?.filter { it.isNotBlank() }
            ?.take(5)
            ?.ifEmpty { listOf("No summary available.") }
            ?: listOf("No summary available."),
        actions = actions
            ?.filterNotNull()
            ?.map { a ->
                ActionItem(
                    description = a.description?.takeIf { it.isNotBlank() }
                        ?: "Action required",
                    deadline = a.deadline?.takeIf { ISO_DATE_REGEX.matches(it) },
                    urgency = parseUrgency(a.urgency)
                )
            }
            ?: emptyList(),
        risks = risks
            ?.filterNotNull()
            ?.filter { it.isNotBlank() }
            ?: emptyList(),
        urgencyLevel = parseUrgency(urgencyLevel),

        // v2 enrichment fields
        sender = sender?.let { dto ->
            SenderInfo(
                name = dto.name?.takeIf { it.isNotBlank() },
                department = dto.department?.takeIf { it.isNotBlank() }
            )
        },
        contacts = contacts
            ?.filterNotNull()
            ?.mapNotNull { dto ->
                val type = parseContactType(dto.type) ?: return@mapNotNull null
                val value = dto.value?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ContactInfo(
                    type = type,
                    value = value,
                    label = dto.label?.takeIf { it.isNotBlank() } ?: type.name.lowercase()
                        .replaceFirstChar { it.uppercase() }
                )
            }
            ?: emptyList(),
        detectedLanguage = detectedLanguage?.takeIf { it.matches(Regex("[a-zA-Z]{2,3}")) },
        confidence = parseConfidence(confidence),
        glossaryTerms = glossaryTerms
            ?.filterNotNull()
            ?.mapNotNull { dto ->
                val term = dto.term?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val explanation = dto.plainExplanation?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                GlossaryTerm(term = term, plainExplanation = explanation)
            }
            ?: emptyList()
    )
}
