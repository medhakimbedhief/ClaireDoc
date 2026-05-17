package com.clairedoc.app.data.db

import com.clairedoc.app.data.model.ActionItem
import com.clairedoc.app.data.model.DocumentResult
import com.clairedoc.app.data.model.SessionStatus
import com.clairedoc.app.data.model.SourceType
import com.clairedoc.app.data.model.UrgencyLevel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

fun DocumentResult.toDocumentSession(
    imageUri: String,
    sourceType: SourceType,
    gson: Gson,
    pageCount: Int? = null
): DocumentSession = DocumentSession(
    id = UUID.randomUUID().toString(),
    createdAt = System.currentTimeMillis(),
    updatedAt = System.currentTimeMillis(),
    sourceType = sourceType,
    imageUri = imageUri,
    documentType = documentType,
    summaryJson = gson.toJson(summary),
    actionsJson = gson.toJson(actions),
    risksJson = gson.toJson(risks),
    urgencyLevel = urgencyLevel.name,
    status = SessionStatus.UNREAD,
    pageCount = pageCount,
    // Persist the complete result so that navigating back from Home or RAG sources
    // restores all v2 fields (contacts, glossaryTerms, confidence, sender, detectedLanguage).
    fullResultJson = gson.toJson(this),
    aiTitle = title
)

/**
 * Reconstructs a [DocumentResult] from this session row.
 *
 * Strategy:
 * 1. If [fullResultJson] is present (sessions saved after migration 4→5), deserialise it
 *    directly — all v2 fields (contacts, glossaryTerms, confidence, …) are restored.
 * 2. Otherwise fall back to the individual JSON columns — only the basic 5 fields are
 *    available (pre-migration rows), which is fine for those older sessions.
 */
fun DocumentSession.toDocumentResult(gson: Gson): DocumentResult {
    if (fullResultJson.isNotBlank()) {
        return runCatching { gson.fromJson(fullResultJson, DocumentResult::class.java) }
            .getOrNull()
            ?.let { result ->
                // `title` may be null for sessions serialised before this field existed —
                // Gson uses Unsafe and bypasses constructor defaults.
                val safeTitle = runCatching { result.title.takeIf { it.isNotBlank() } }.getOrNull()
                if (safeTitle != null) result
                else result.copy(title = aiTitle.ifBlank { humanizeDocType(documentType) })
            }
            ?: buildLegacyResult(gson)
    }
    return buildLegacyResult(gson)
}

private fun DocumentSession.buildLegacyResult(gson: Gson): DocumentResult {
    val listStringType = object : TypeToken<List<String>>() {}.type
    val listActionType = object : TypeToken<List<ActionItem>>() {}.type
    return DocumentResult(
        documentType = documentType,
        title = aiTitle.ifBlank { humanizeDocType(documentType) },
        summary = gson.fromJson(summaryJson, listStringType),
        actions = gson.fromJson(actionsJson, listActionType),
        risks = gson.fromJson(risksJson, listStringType),
        urgencyLevel = UrgencyLevel.valueOf(urgencyLevel)
    )
}

private fun humanizeDocType(type: String): String =
    type.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        .ifBlank { "Scanned Document" }
