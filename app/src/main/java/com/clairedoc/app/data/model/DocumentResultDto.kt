package com.clairedoc.app.data.model

/**
 * Gson-deserializable DTO for the raw JSON emitted by Gemma 4 E2B.
 * All fields are nullable to survive malformed / partial model output.
 * Call [toDomain] to map into the validated [DocumentResult].
 */
data class ActionItemDto(
    val description: String?,
    val deadline: String?,
    val urgency: String?
)

data class DocumentResultDto(
    val documentType: String?,
    val summary: List<String?>?,
    val actions: List<ActionItemDto?>?,
    val risks: List<String?>?,
    val urgencyLevel: String?
)

private val VALID_DOCUMENT_TYPES = setOf("BILL", "CONTRACT", "LEGAL_NOTICE", "MEDICAL", "OTHER")
private val ISO_DATE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")

private fun parseUrgency(raw: String?): UrgencyLevel =
    UrgencyLevel.entries.firstOrNull { it.name == raw?.uppercase().orEmpty() }
        ?: UrgencyLevel.YELLOW

fun DocumentResultDto.toDomain(): DocumentResult {
    val docType = documentType?.uppercase().orEmpty()
        .let { if (it in VALID_DOCUMENT_TYPES) it else "OTHER" }

    return DocumentResult(
        documentType = docType,
        summary = summary
            ?.filterNotNull()
            ?.filter { it.isNotBlank() }
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
        urgencyLevel = parseUrgency(urgencyLevel)
    )
}
