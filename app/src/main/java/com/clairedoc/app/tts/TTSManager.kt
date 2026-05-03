package com.clairedoc.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.clairedoc.app.data.model.Confidence
import com.clairedoc.app.data.model.DocumentResult
import com.clairedoc.app.data.model.GlossaryTerm
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

    // Non-blocking readiness flag for the fire-and-forget speak(DocumentResult) overload.
    @Volatile private var isReady = false

    // Kept for the suspending speak(String) overload.
    private val initDeferred = CompletableDeferred<Boolean>()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                initDeferred.complete(true)
                // Apply device locale at startup — speak(DocumentResult) will override this
                // per-document when it runs.
                applyLocale(Locale.getDefault())
                Log.d(TAG, "TTS initialised")
            } else {
                initDeferred.complete(false)
                Log.e(TAG, "TTS init failed, status=$status")
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Speaks the full [DocumentResult] in the document's own language:
     *
     * 1. Low-confidence warning  (only when [DocumentResult.confidence] == LOW)
     * 2. Document type announcement
     * 3. All summary bullets
     * 4. First action item, with deadline appended if present
     * 5. First risk/warning
     *
     * The TTS locale is set to match [DocumentResult.detectedLanguage] and validated
     * against the device's installed language packs; falls back to English if the
     * required pack is absent.
     *
     * [onComplete] is called on the TTS callback thread when the last utterance ends.
     * It is also called immediately if there is nothing to speak.
     */
    fun speak(result: DocumentResult, onComplete: (() -> Unit)? = null) {
        if (!isReady) {
            Log.w(TAG, "TTS not ready, skipping speak(result)")
            onComplete?.invoke()
            return
        }
        val t = tts ?: run { onComplete?.invoke(); return }

        // Switch locale to the document language before queuing anything.
        Log.d(TAG, "Document detectedLanguage = ${result.detectedLanguage}")
        applyLocale(resolveLocale(result.detectedLanguage))

        val utterances = buildUtteranceList(result)
        if (utterances.isEmpty()) { onComplete?.invoke(); return }

        // Register completion listener on the last utterance so callers can react.
        if (onComplete != null) {
            val lastId = "utterance_${utterances.lastIndex}"
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String) {}
                override fun onDone(id: String) {
                    if (id == lastId) onComplete()
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String) {
                    if (id == lastId) onComplete()
                }
                override fun onError(id: String, errorCode: Int) {
                    if (id == lastId) onComplete()
                }
            })
        }

        utterances.forEachIndexed { index, text ->
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            t.speak(text, queueMode, null, "utterance_$index")
        }
        Log.d(TAG, "Queued ${utterances.size} utterances")
    }

    /**
     * Speaks a glossary [term] and its plain explanation in the document's [language].
     * Interrupts any ongoing speech.
     */
    fun speakTerm(term: GlossaryTerm, language: String?) {
        if (!isReady) { Log.w(TAG, "TTS not ready, skipping speakTerm"); return }
        val t = tts ?: return
        applyLocale(resolveLocale(language))
        t.speak("${term.term}: ${term.plainExplanation}", TextToSpeech.QUEUE_FLUSH, null, "term")
    }

    /**
     * Speaks a single [text] string and suspends until the utterance completes.
     *
     * Kept for backward compatibility. Prefer [speak(DocumentResult)] for full results.
     */
    suspend fun speak(text: String) {
        val ready = initDeferred.await()
        if (!ready) { Log.w(TAG, "TTS not ready, skipping: $text"); return }
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

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Sets [locale] on the TTS engine after confirming the language pack is installed.
     * Silently falls back to [Locale.ENGLISH] when the pack is absent.
     */
    private fun applyLocale(locale: Locale) {
        val t = tts ?: return
        val result = t.isLanguageAvailable(locale)
        t.language = if (result >= TextToSpeech.LANG_AVAILABLE) locale else Locale.ENGLISH
        Log.d(TAG, "TTS locale → $locale (availability=$result)")
    }

    /**
     * Maps an ISO 639-1 language code to a [Locale].
     * Returns [Locale.ENGLISH] for unknown or null codes.
     */
    private fun resolveLocale(lang: String?): Locale = when (lang?.lowercase()?.trim()) {
        "fr"            -> Locale.FRENCH
        "de"            -> Locale.GERMAN
        "es"            -> Locale("es")
        "ar"            -> Locale("ar")
        "tr"            -> Locale("tr")
        "it"            -> Locale.ITALIAN
        "zh", "zh-cn"   -> Locale.CHINESE
        "zh-tw"         -> Locale.TRADITIONAL_CHINESE
        "ja"            -> Locale.JAPANESE
        "ko"            -> Locale.KOREAN
        "nl"            -> Locale("nl")
        "pt", "pt-br"   -> Locale("pt")
        "ru"            -> Locale("ru")
        "pl"            -> Locale("pl")
        "hi"            -> Locale("hi")
        else            -> Locale.ENGLISH
    }

    /**
     * Builds the ordered utterance list for [result].
     *
     * Ordering:
     * 1. Low-confidence warning (when [DocumentResult.confidence] == [Confidence.LOW])
     * 2. Document type: "This is a [type]."
     * 3. Summary bullets (all of them)
     * 4. First action item: "Important: [description] by [deadline]."
     * 5. First risk: "Warning: [risk]"
     */
    private fun buildUtteranceList(result: DocumentResult): List<String> {
        val list = mutableListOf<String>()

        // 1. Low-confidence warning — must come first so the user calibrates trust
        if (result.confidence == Confidence.LOW) {
            list.add(
                "Warning: the document image quality is low. " +
                "Some information may be inaccurate."
            )
        }

        // 2. Document type
        list.add("This is a ${result.documentType.replace('_', ' ').lowercase()}.")

        // 3. All summary bullets
        result.summary.forEach { list.add(it) }

        // 4. First action with optional deadline
        result.actions.firstOrNull()?.let { action ->
            val deadline = action.deadline?.let { " by $it" } ?: ""
            list.add("Important: ${action.description}$deadline.")
        }

        // 5. First risk
        result.risks.firstOrNull()?.let { risk ->
            list.add("Warning: $risk")
        }

        return list
    }
}
