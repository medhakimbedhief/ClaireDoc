package com.clairedoc.app.di

import android.content.Context
import androidx.room.Room
import com.clairedoc.app.data.db.AppDatabase
import com.clairedoc.app.data.db.DocumentSessionDao
import com.clairedoc.app.data.db.FtsChunkDao
import com.clairedoc.app.data.db.MIGRATION_1_2
import com.clairedoc.app.data.db.MIGRATION_2_3
import com.clairedoc.app.data.db.MIGRATION_3_4
import com.clairedoc.app.data.db.MIGRATION_4_5
import com.clairedoc.app.data.db.MIGRATION_5_6
import com.clairedoc.app.data.db.MIGRATION_6_7
import com.clairedoc.app.engine.LiteRTEngine
import com.clairedoc.app.engine.ModelDownloadManager
import com.clairedoc.app.pipeline.DocumentAnalyzer
import com.clairedoc.app.pipeline.JsonParser
import com.clairedoc.app.pipeline.PromptBuilder
import com.clairedoc.app.tts.TTSManager
import com.clairedoc.app.rag.MyObjectBox
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.objectbox.BoxStore
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideGson(): Gson = GsonBuilder().create()

    @Singleton
    @Provides
    fun provideLiteRTEngine(
        @ApplicationContext context: Context
    ): LiteRTEngine = LiteRTEngine(context)

    @Singleton
    @Provides
    fun provideModelDownloadManager(
        @ApplicationContext context: Context
    ): ModelDownloadManager = ModelDownloadManager(context)

    @Singleton
    @Provides
    fun provideTTSManager(
        @ApplicationContext context: Context
    ): TTSManager = TTSManager(context)

    @Singleton
    @Provides
    fun providePromptBuilder(): PromptBuilder = PromptBuilder()

    @Singleton
    @Provides
    fun provideJsonParser(gson: Gson): JsonParser = JsonParser(gson)

    @Singleton
    @Provides
    fun provideDocumentAnalyzer(
        engine: LiteRTEngine,
        promptBuilder: PromptBuilder,
        jsonParser: JsonParser
    ): DocumentAnalyzer = DocumentAnalyzer(engine, promptBuilder, jsonParser)

    @Singleton
    @Provides
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "clairedoc.db"
    )
        // Use bundled SQLite (io.requery:sqlite-android) which is compiled with
        // SQLITE_ENABLE_FTS5. Required because OEM builds and emulator images
        // may ship system SQLite without the fts5 module.
        .openHelperFactory(RequerySQLiteOpenHelperFactory())
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
        .build()

    @Singleton
    @Provides
    fun provideDocumentSessionDao(db: AppDatabase): DocumentSessionDao =
        db.documentSessionDao()

    @Singleton
    @Provides
    fun provideFtsChunkDao(db: AppDatabase): FtsChunkDao =
        db.ftsChunkDao()

    // ObjectBox vector store — MyObjectBox is generated at compile time by the
    // ObjectBox Gradle plugin from @Entity classes in com.clairedoc.app.rag.*
    @Singleton
    @Provides
    fun provideObjectBoxStore(
        @ApplicationContext context: Context
    ): BoxStore = MyObjectBox.builder().androidContext(context).build()
}
