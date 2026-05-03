package com.clairedoc.app.ui.result

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clairedoc.app.actions.EmailDraft
import com.clairedoc.app.actions.EmailDraftBuilder
import com.clairedoc.app.data.model.ContactType
import com.clairedoc.app.data.model.DocumentResult
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class EmailDraftUiState {
    object Idle : EmailDraftUiState()
    object Generating : EmailDraftUiState()
    data class Ready(val draft: EmailDraft, val isDirty: Boolean = false) : EmailDraftUiState()
    data class Error(val message: String) : EmailDraftUiState()
}

/**
 * Handles email draft generation state for [ResultScreen].
 *
 * Reads the same [resultJson] nav arg as [ResultViewModel] via [SavedStateHandle]
 * so it has access to v2 [DocumentResult.contacts] (including email addresses)
 * that are not persisted to Room.
 */
@HiltViewModel
class EmailActionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val emailDraftBuilder: EmailDraftBuilder,
    private val gson: Gson
) : ViewModel() {

    private val _uiState = MutableStateFlow<EmailDraftUiState>(EmailDraftUiState.Idle)
    val uiState: StateFlow<EmailDraftUiState> = _uiState.asStateFlow()

    private val _emailAddresses = MutableStateFlow<List<String>>(emptyList())
    val emailAddresses: StateFlow<List<String>> = _emailAddresses.asStateFlow()

    private val _selectedEmail = MutableStateFlow("")
    val selectedEmail: StateFlow<String> = _selectedEmail.asStateFlow()

    /** Holds the DocumentResult decoded from the nav arg to build the prompt. */
    private var documentResult: DocumentResult? = null

    init {
        // Read the same resultJson nav arg that ResultViewModel uses
        val encoded = savedStateHandle.get<String>("resultJson").orEmpty()
        val json = Uri.decode(encoded)
        if (json.isNotBlank()) {
            runCatching { gson.fromJson(json, DocumentResult::class.java) }
                .onSuccess { result ->
                    documentResult = result
                    val emails = result.contacts.orEmpty()
                        .filter { it.type == ContactType.EMAIL }
                        .map { it.value }
                    _emailAddresses.value = emails
                    _selectedEmail.value = emails.firstOrNull().orEmpty()
                }
        }
    }

    fun selectEmail(email: String) {
        _selectedEmail.value = email
    }

    /**
     * Starts draft generation. If the user has already edited a generated draft
     * (isDirty == true), this call is a no-op — the caller must present a
     * confirmation dialog and then call [confirmRegenerate].
     */
    fun generateDraft(userIntent: String) {
        val result = documentResult ?: return
        val email = _selectedEmail.value.takeIf { it.isNotBlank() } ?: return
        val current = _uiState.value
        if (current is EmailDraftUiState.Ready && current.isDirty) {
            // Let the UI show the confirm dialog; actual generation triggered by confirmRegenerate()
            return
        }
        startGeneration(result, email, userIntent)
    }

    /** Called after the user confirms they are happy to discard their manual edits. */
    fun confirmRegenerate(userIntent: String) {
        val result = documentResult ?: return
        val email = _selectedEmail.value.takeIf { it.isNotBlank() } ?: return
        startGeneration(result, email, userIntent)
    }

    private fun startGeneration(result: DocumentResult, email: String, userIntent: String) {
        _uiState.value = EmailDraftUiState.Generating
        viewModelScope.launch {
            emailDraftBuilder.generateDraft(result, email, userIntent)
                .onSuccess { draft -> _uiState.value = EmailDraftUiState.Ready(draft) }
                .onFailure { e ->
                    _uiState.value = EmailDraftUiState.Error(
                        e.message?.take(120) ?: "Draft generation failed. Please try again."
                    )
                }
        }
    }

    /** Called when the user edits the draft body directly in the UI. */
    fun updateDraftBody(newBody: String) {
        val current = _uiState.value as? EmailDraftUiState.Ready ?: return
        _uiState.value = current.copy(
            draft = current.draft.copy(body = newBody),
            isDirty = true
        )
    }

    fun resetDraft() {
        _uiState.value = EmailDraftUiState.Idle
    }
}
