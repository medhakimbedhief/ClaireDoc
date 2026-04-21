package com.clairedoc.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume

private const val TAG = "ClaireDoc_TTS"

class TTSManager @Inject constructor(
    @ApplicationContext private val context: Context
) : Closeable {

    private var tts: TextToSpeech? = null

    // Resolved to true once TextToSpeech.OnInitListener fires SUCCESS,
    // false on any failure. speak() always awaits this before proceeding.
    private val initDeferred = CompletableDeferred<Boolean>()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = tts?.isLanguageAvailable(Locale.getDefault())?.let {
                    if (it >= TextToSpeech.LANG_AVAILABLE) Locale.getDefault() else Locale.US
                } ?: Locale.US
                tts?.language = locale
                initDeferred.complete(true)
                Log.d(TAG, "TTS initialised, locale=$locale")
            } else {
                initDeferred.complete(false)
                Log.e(TAG, "TTS init failed, status=$status")
            }
        }
    }

    /**
     * Speaks [text] and suspends until the utterance finishes.
     * Safe to call in a coroutine loop — each call waits for the previous
     * utterance to complete before returning.
     */
    suspend fun speak(text: String) {
        val ready = initDeferred.await()
        if (!ready) {
            Log.w(TAG, "TTS not ready, skipping: $text")
            return
        }
        if (text.isBlank()) return

        suspendCancellableCoroutine<Unit> { cont ->
            val utteranceId = UUID.randomUUID().toString()

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {}
                override fun onDone(utteranceId: String) {
                    if (cont.isActive) cont.resume(Unit)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) {
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onError(utteranceId: String, errorCode: Int) {
                    if (cont.isActive) cont.resume(Unit)
                }
            })

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

            cont.invokeOnCancellation { tts?.stop() }
        }
    }

    fun stop() {
        tts?.stop()
        Log.d(TAG, "TTS stopped")
    }

    override fun close() {
        tts?.shutdown()
        tts = null
        Log.d(TAG, "TTS shut down")
    }
}
