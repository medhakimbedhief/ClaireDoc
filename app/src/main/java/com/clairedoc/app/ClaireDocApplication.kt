package com.clairedoc.app

import android.app.Application
import com.clairedoc.app.data.repository.DocumentSessionRepository
import com.clairedoc.app.engine.LiteRTEngine
import com.clairedoc.app.rag.ChunkRepository
import com.clairedoc.app.rag.IndexingWorker
import com.clairedoc.app.tts.TTSManager
import dagger.hilt.android.HiltAndroidApp
import io.objectbox.BoxStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ClaireDocApplication : Application() {

    @Inject lateinit var liteRTEngine: LiteRTEngine
    @Inject lateinit var ttsManager: TTSManager
    @Inject lateinit var boxStore: BoxStore
    @Inject lateinit var sessionRepository: DocumentSessionRepository
    @Inject lateinit var chunkRepository: ChunkRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Recover sessions that were saved before WorkManager was wired up, or whose
        // indexing job was lost (process killed, storage-low constraint not met, etc.).
        applicationScope.launch {
            val sessions = sessionRepository.getAllSessionsSnapshot()
            sessions.forEach { session ->
                if (!chunkRepository.isIndexed(session.id)) {
                    IndexingWorker.enqueue(this@ClaireDocApplication, session.id)
                }
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        liteRTEngine.close()
        ttsManager.close()
        boxStore.close()
    }
}
