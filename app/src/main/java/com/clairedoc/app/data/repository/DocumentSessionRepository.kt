package com.clairedoc.app.data.repository

import android.content.Context
import com.clairedoc.app.data.db.DocumentSession
import com.clairedoc.app.data.db.DocumentSessionDao
import com.clairedoc.app.data.db.toDocumentSession
import com.clairedoc.app.data.model.ChatMessage
import com.clairedoc.app.data.model.DocumentResult
import com.clairedoc.app.data.model.SessionStatus
import com.clairedoc.app.data.model.SourceType
import com.clairedoc.app.rag.IndexingWorker
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentSessionRepository @Inject constructor(
    private val dao: DocumentSessionDao,
    private val gson: Gson,
    @ApplicationContext private val context: Context
) {
    fun getAllSessions(): Flow<List<DocumentSession>> = dao.getAll()

    fun getSession(id: String): Flow<DocumentSession?> = dao.getById(id)

    /**
     * Persists [result] as a new [DocumentSession] and returns the generated ID.
     */
    suspend fun saveSession(
        result: DocumentResult,
        imageUri: String,
        sourceType: SourceType,
        pageCount: Int? = null
    ): String {
        val session = result.toDocumentSession(imageUri, sourceType, gson, pageCount)
        dao.insert(session)
        // Enqueue background embedding immediately after persist.
        // ExistingWorkPolicy.KEEP (default) — safe to call multiple times for the same session.
        IndexingWorker.enqueue(context, session.id)
        return session.id
    }

    /**
     * Returns a one-shot snapshot of all unarchived sessions.
     * Used by [IndexingWorker] and app-startup recovery — avoids keeping a hot Flow alive.
     */
    suspend fun getAllSessionsSnapshot(): List<DocumentSession> = dao.getAll().first()

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

    /** Clears the user-supplied title, reverting the UI to the AI-generated [DocumentSession.aiTitle]. */
    suspend fun clearUserTitle(id: String) =
        dao.clearUserTitle(id, System.currentTimeMillis())
}
