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
    val isArchived: Boolean = false
)
