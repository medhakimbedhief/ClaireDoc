package com.clairedoc.app.data.db

import android.util.Log
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DocumentSession::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(DocumentSessionConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentSessionDao(): DocumentSessionDao
    abstract fun ftsChunkDao(): FtsChunkDao
}

/**
 * Adds the [chunk_fts] FTS5 virtual table for BM25 keyword search.
 *
 * Using a regular (not contentless) FTS5 table so that DELETE is supported.
 * [chunk_id] and [session_id] are stored UNINDEXED — only [text] is full-text indexed.
 *
 * FTS5 is wrapped in try-catch: some OEM builds and older emulator images ship SQLite
 * without the fts5 module compiled in. When unavailable the table is simply not created
 * and [ChunkRepository] degrades to vector-only search automatically.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        try {
            database.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS chunk_fts " +
                "USING fts5(text, chunk_id UNINDEXED, session_id UNINDEXED)"
            )
        } catch (e: Exception) {
            // FTS5 not compiled into this device's SQLite — BM25 search disabled,
            // vector-only retrieval remains fully functional.
            Log.w("ClaireDoc_DB", "FTS5 unavailable — BM25 search disabled: ${e.message}")
        }
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE document_sessions ADD COLUMN aiTitle TEXT NOT NULL DEFAULT ''"
        )
    }
}

/**
 * Adds the [DocumentSession.fullResultJson] column.
 * Existing rows get an empty string default — [DocumentSession.toDocumentResult] falls
 * back to the individual JSON columns for those rows.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE document_sessions ADD COLUMN fullResultJson TEXT NOT NULL DEFAULT ''"
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE document_sessions ADD COLUMN pageCount INTEGER")
    }
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
