package com.clairedoc.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DocumentSession::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(DocumentSessionConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentSessionDao(): DocumentSessionDao
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE document_sessions ADD COLUMN chatHistoryJson TEXT NOT NULL DEFAULT '[]'"
        )
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS document_history")
        database.execSQL(
            """
            CREATE TABLE document_sessions (
                id TEXT NOT NULL PRIMARY KEY,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                sourceType TEXT NOT NULL,
                imageUri TEXT NOT NULL,
                originalFileName TEXT,
                documentType TEXT NOT NULL,
                summaryJson TEXT NOT NULL,
                actionsJson TEXT NOT NULL,
                risksJson TEXT NOT NULL,
                urgencyLevel TEXT NOT NULL,
                status TEXT NOT NULL,
                userTitle TEXT,
                isArchived INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
}
