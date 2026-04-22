package com.clairedoc.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clairedoc.app.data.db.DocumentSession
import com.clairedoc.app.data.db.toDocumentResult
import com.clairedoc.app.data.model.ActionItem
import com.clairedoc.app.data.model.SessionStatus
import com.clairedoc.app.data.repository.DocumentSessionRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

sealed class HomeUiState {
    object Loading : HomeUiState()
    object Empty : HomeUiState()
    data class Sessions(val items: List<HomeSessionItem>) : HomeUiState()
}

data class HomeSessionItem(
    val session: DocumentSession,
    val resultJson: String,
    val nearestDeadlineDays: Int?,
    val firstActionDescription: String?
)

private val STATUS_ORDER = mapOf(
    SessionStatus.OVERDUE     to 0,
    SessionStatus.UNREAD      to 1,
    SessionStatus.IN_PROGRESS to 2,
    SessionStatus.DONE        to 3
)

private val ISO_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val ACTION_LIST_TYPE = object : TypeToken<List<ActionItem>>() {}.type

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DocumentSessionRepository,
    private val gson: Gson
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            checkAndUpdateOverdueStatus()
            observeSessions()
        }
    }

    private suspend fun checkAndUpdateOverdueStatus() {
        val today = LocalDate.now()
        repository.getAllSessions().first().forEach { session ->
            if (session.status == SessionStatus.DONE || session.status == SessionStatus.OVERDUE) return@forEach
            val earliest = parseActions(session.actionsJson)
                .mapNotNull { it.deadline }
                .mapNotNull { runCatching { LocalDate.parse(it, ISO_DATE_FMT) }.getOrNull() }
                .minOrNull() ?: return@forEach
            if (earliest.isBefore(today)) {
                repository.updateSessionStatus(session.id, SessionStatus.OVERDUE)
            }
        }
    }

    private suspend fun observeSessions() {
        repository.getAllSessions().collect { list ->
            if (list.isEmpty()) {
                _uiState.value = HomeUiState.Empty
                return@collect
            }
            val today = LocalDate.now()
            val items = list.map { session ->
                val actions = parseActions(session.actionsJson)
                val deadlineDays = actions
                    .mapNotNull { it.deadline }
                    .mapNotNull { runCatching { LocalDate.parse(it, ISO_DATE_FMT) }.getOrNull() }
                    .minOrNull()
                    ?.let { ChronoUnit.DAYS.between(today, it).toInt() }
                val resultJson = runCatching { gson.toJson(session.toDocumentResult(gson)) }
                    .getOrElse { "{}" }
                HomeSessionItem(
                    session = session,
                    resultJson = resultJson,
                    nearestDeadlineDays = deadlineDays,
                    firstActionDescription = actions.firstOrNull()?.description
                )
            }.sortedWith(
                compareBy(
                    { STATUS_ORDER[it.session.status] ?: 99 },
                    { -it.session.createdAt }
                )
            )
            _uiState.value = HomeUiState.Sessions(items)
        }
    }

    private fun parseActions(json: String): List<ActionItem> =
        runCatching { gson.fromJson<List<ActionItem>>(json, ACTION_LIST_TYPE) }
            .getOrElse { emptyList() }

    fun markInProgress(id: String) {
        viewModelScope.launch { repository.updateSessionStatus(id, SessionStatus.IN_PROGRESS) }
    }

    fun markDone(id: String) {
        viewModelScope.launch { repository.updateSessionStatus(id, SessionStatus.DONE) }
    }

    fun renameSession(id: String, title: String) {
        viewModelScope.launch { repository.renameSession(id, title) }
    }

    fun archiveSession(id: String) {
        viewModelScope.launch { repository.archiveSession(id) }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch { repository.deleteSession(id) }
    }
}
