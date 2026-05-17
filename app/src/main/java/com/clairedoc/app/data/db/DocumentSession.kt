package com.clairedoc.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.clairedoc.app.data.model.SessionStatus
import com.clairedoc.app.data.model.SourceType
import java.util.UUID

@Entity(tableName = "document_sessions")
data class DocumentSession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sourceType: SourceType,
    val imageUri: String,
    val originalFileName: String? = null,
    val documentType: String,
    val summaryJson: String,        // JSON array of plain-language bullets
    val actionsJson: String,        // JSON array of ActionItem
    val risksJson: String,          // JSON array of risk strings
    val urgencyLevel: String,       // "RED" | "YELLOW" | "GREEN"
    val status: SessionStatus = SessionStatus.UNREAD,
    val userTitle: String? = null,
    val isArchived: Boolean = false,
    val chatHistoryJson: String = "[]",  // JSON array of ChatMessage
    val pageCount: Int? = null,
    /**
     * Full [DocumentResult] serialised to JSON at scan time.
     * Includes all v2 fields (contacts, glossaryTerms, confidence, sender, detectedLanguage)
     * that are NOT stored individually. Empty string for sessions saved before this column
     * was added (migration 4→5) — [toDocumentResult] falls back gracefully in that case.
     */
    val fullResultJson: String = "",
    /**
     * AI-generated short title (3–6 words) produced by the model at scan time.
     * Empty string for sessions saved before migration 5→6 — [displayTitle] falls back to
     * [documentType] for those rows.
     * UI should always use [displayTitle], never this field directly.
     */
    val aiTitle: String = ""
) {
    /**
     * The title to show everywhere in the UI.
     * Priority: user-set title → AI title → humanised document type → "Scanned Document".
     */
    val displayTitle: String
        get() = userTitle?.takeIf { it.isNotBlank() }
            ?: aiTitle.ifBlank {
                documentType.replace("_", " ").lowercase()
                    .replaceFirstChar { it.uppercase() }
                    .ifBlank { "Scanned Document" }
            }
}
