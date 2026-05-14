package com.clairedoc.app

import android.app.Application
import com.clairedoc.app.engine.LiteRTEngine
import com.clairedoc.app.tts.TTSManager
import dagger.hilt.android.HiltAndroidApp
import io.objectbox.BoxStore
import javax.inject.Inject

@HiltAndroidApp
class ClaireDocApplication : Application() {

    @Inject lateinit var liteRTEngine: LiteRTEngine
    @Inject lateinit var ttsManager: TTSManager
    @Inject lateinit var boxStore: BoxStore

    override fun onTerminate() {
        super.onTerminate()
        liteRTEngine.close()
        ttsManager.close()
        boxStore.close()
    }
}
