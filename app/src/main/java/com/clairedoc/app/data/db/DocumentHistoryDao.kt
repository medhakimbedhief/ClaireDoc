package com.clairedoc.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy

/** Scaffold DAO — extended once document history UI is built post-hackathon. */
@Dao
interface DocumentHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DocumentHistoryEntity): Long
}
