package com.clairedoc.app.ui.result

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clairedoc.app.data.model.DocumentResult
import com.clairedoc.app.tts.TTSManager
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gson: Gson,
    private val ttsManager: TTSManager
) : ViewModel() {

    private val _result = MutableStateFlow<DocumentResult?>(null)
    val result: StateFlow<DocumentResult?> = _result.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        // Navigation passes the JSON URL-encoded inside the route path.
        // Uri.decode recovers the original JSON string.
        val encoded = savedStateHandle.get<String>("resultJson").orEmpty()
        val json = Uri.decode(encoded)
        if (json.isNotBlank()) {
            runCatching { gson.fromJson(json, DocumentResult::class.java) }
                .onSuccess { _result.value = it }
        }
    }

    fun toggleTTS() {
        if (_isSpeaking.value) {
            ttsManager.stop()
            _isSpeaking.value = false
        } else {
            val r = _result.value ?: return
            startReading(r)
        }
    }

    private fun startReading(result: DocumentResult) {
        viewModelScope.launch {
            _isSpeaking.value = true
            ttsManager.speak(
                "This is a ${result.documentType.replace("_", " ").lowercase()}"
            )
            result.summary.forEach { bullet -> ttsManager.speak(bullet) }
            result.actions.firstOrNull()?.let { action ->
                val msg = buildString {
                    append("Important: ${action.description}")
                    action.deadline?.let { append(", due $it") }
                }
                ttsManager.speak(msg)
            }
            result.risks.firstOrNull()?.let { risk ->
                ttsManager.speak("Warning: $risk")
            }
            _isSpeaking.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
    }
}
