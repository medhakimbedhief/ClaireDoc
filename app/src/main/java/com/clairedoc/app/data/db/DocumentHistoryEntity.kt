package com.clairedoc.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Scaffold entity for future document history feature.
 * Required by Room to compile the database — not actively used in MVP.
 */
@Entity(tableName = "document_history")
data class DocumentHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentType: String,
    val analyzedAt: Long,       // epoch millis
    val urgencyLevel: String,
    val summaryJson: String     // serialised List<String>
)
