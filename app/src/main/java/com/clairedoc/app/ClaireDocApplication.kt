package com.clairedoc.app

import android.app.Application
import com.clairedoc.app.engine.LiteRTEngine
import com.clairedoc.app.tts.TTSManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ClaireDocApplication : Application() {

    @Inject lateinit var liteRTEngine: LiteRTEngine
    @Inject lateinit var ttsManager: TTSManager

    override fun onTerminate() {
        super.onTerminate()
        liteRTEngine.close()
        ttsManager.close()
    }
}
