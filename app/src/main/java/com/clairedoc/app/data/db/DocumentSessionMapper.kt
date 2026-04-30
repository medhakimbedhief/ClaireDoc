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
    pageCount = pageCount
)

fun DocumentSession.toDocumentResult(gson: Gson): DocumentResult {
    val listStringType = object : TypeToken<List<String>>() {}.type
    val listActionType = object : TypeToken<List<ActionItem>>() {}.type
    return DocumentResult(
        documentType = documentType,
        summary = gson.fromJson(summaryJson, listStringType),
        actions = gson.fromJson(actionsJson, listActionType),
        risks = gson.fromJson(risksJson, listStringType),
        urgencyLevel = UrgencyLevel.valueOf(urgencyLevel)
    )
}
