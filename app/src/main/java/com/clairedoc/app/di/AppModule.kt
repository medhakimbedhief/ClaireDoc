package com.clairedoc.app.di

import android.content.Context
import androidx.room.Room
import com.clairedoc.app.data.db.AppDatabase
import com.clairedoc.app.data.db.DocumentSessionDao
import com.clairedoc.app.data.db.MIGRATION_1_2
import com.clairedoc.app.data.db.MIGRATION_2_3
import com.clairedoc.app.data.db.MIGRATION_3_4
import com.clairedoc.app.engine.LiteRTEngine
import com.clairedoc.app.engine.ModelDownloadManager
import com.clairedoc.app.pipeline.DocumentAnalyzer
import com.clairedoc.app.pipeline.JsonParser
import com.clairedoc.app.pipeline.PromptBuilder
import com.clairedoc.app.tts.TTSManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()

    @Singleton
    @Provides
    fun provideDocumentSessionDao(db: AppDatabase): DocumentSessionDao =
        db.documentSessionDao()
}
