package com.clairedoc.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DocumentHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentHistoryDao(): DocumentHistoryDao
}
