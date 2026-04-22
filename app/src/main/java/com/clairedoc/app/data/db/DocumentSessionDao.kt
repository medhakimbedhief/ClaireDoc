package com.clairedoc.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.clairedoc.app.data.model.SessionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: DocumentSession)

    @Update
    suspend fun update(session: DocumentSession)

    @Query("DELETE FROM document_sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM document_sessions WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun getAll(): Flow<List<DocumentSession>>

    @Query("SELECT * FROM document_sessions WHERE id = :id")
    fun getById(id: String): Flow<DocumentSession?>

    @Query("UPDATE document_sessions SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: SessionStatus, updatedAt: Long)

    @Query("UPDATE document_sessions SET userTitle = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE document_sessions SET isArchived = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archive(id: String, updatedAt: Long)
}
