package com.clairedoc.app.data.repository

import com.clairedoc.app.data.db.DocumentSession
import com.clairedoc.app.data.db.DocumentSessionDao
import com.clairedoc.app.data.db.toDocumentSession
import com.clairedoc.app.data.model.ChatMessage
import com.clairedoc.app.data.model.DocumentResult
import com.clairedoc.app.data.model.SessionStatus
import com.clairedoc.app.data.model.SourceType
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentSessionRepository @Inject constructor(
    private val dao: DocumentSessionDao,
    private val gson: Gson
) {
    fun getAllSessions(): Flow<List<DocumentSession>> = dao.getAll()

    fun getSession(id: String): Flow<DocumentSession?> = dao.getById(id)

    /**
     * Persists [result] as a new [DocumentSession] and returns the generated ID.
     */
    suspend fun saveSession(
        result: DocumentResult,
        imageUri: String,
        sourceType: SourceType
    ): String {
        val session = result.toDocumentSession(imageUri, sourceType, gson)
        dao.insert(session)
        return session.id
    }

    suspend fun updateSessionStatus(id: String, status: SessionStatus) =
        dao.updateStatus(id, status, System.currentTimeMillis())

    suspend fun renameSession(id: String, title: String) =
        dao.updateTitle(id, title, System.currentTimeMillis())

    suspend fun archiveSession(id: String) =
        dao.archive(id, System.currentTimeMillis())

    suspend fun deleteSession(id: String) = dao.delete(id)

    suspend fun updateChatHistory(id: String, messages: List<ChatMessage>) {
        val json = gson.toJson(messages)
        dao.updateChatHistory(id, json, System.currentTimeMillis())
    }
}
