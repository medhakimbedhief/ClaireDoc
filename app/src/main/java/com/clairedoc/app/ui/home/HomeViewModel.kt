package com.clairedoc.app.ui.home

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.clairedoc.app.data.db.DocumentSession
import com.clairedoc.app.data.db.toDocumentResult
import com.clairedoc.app.data.model.ActionItem
import com.clairedoc.app.data.model.SessionStatus
import com.clairedoc.app.data.repository.DocumentSessionRepository
import com.clairedoc.app.rag.ChunkRepository
import com.clairedoc.app.rag.IndexingWorker
import com.clairedoc.app.rag.TAG_RAG_INDEXING
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
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
    val firstActionDescription: String?,
    val isIndexed: Boolean = false
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
    private val chunkRepository: ChunkRepository,
    private val gson: Gson,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)

    /** Current search query — updated by [updateSearchQuery]. Empty string = no filter. */
    val searchQuery = MutableStateFlow("")

    /**
     * Filtered view of sessions. When [searchQuery] is blank the full grouped list is returned
     * unchanged. When non-blank, items are filtered across title / document type / first action
     * and sorted by relevance (title hits first, then status order).
     */
    val uiState: StateFlow<HomeUiState> = combine(_uiState, searchQuery) { state, query ->
        if (query.isBlank() || state !is HomeUiState.Sessions) {
            state
        } else {
            val q = query.lowercase(Locale.getDefault())
            val filtered = state.items.filter { item ->
                item.session.displayTitle.lowercase(Locale.getDefault()).contains(q) ||
                item.session.documentType.lowercase(Locale.getDefault()).contains(q) ||
                item.firstActionDescription?.lowercase(Locale.getDefault())?.contains(q) == true
            }
            // Title matches rank above description / type matches
            val sorted = filtered.sortedWith(
                compareByDescending<HomeSessionItem> {
                    it.session.displayTitle.lowercase(Locale.getDefault()).contains(q)
                }.thenBy { STATUS_ORDER[it.session.status] ?: 99 }
            )
            HomeUiState.Sessions(sorted)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState.Loading)

    /** Incremented when a WorkManager indexing job succeeds — triggers isIndexed re-check. */
    private val _indexingRefresh = MutableStateFlow(0)

    /** True while at least one indexing job is RUNNING or ENQUEUED. */
    private val _isIndexingActive = MutableStateFlow(false)
    val isIndexingActive: StateFlow<Boolean> = _isIndexingActive.asStateFlow()

    private val overdueObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            viewModelScope.launch { checkAndUpdateOverdueStatus() }
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(overdueObserver)
        viewModelScope.launch { observeSessions() }
        viewModelScope.launch { observeIndexingWork() }
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
        repository.getAllSessions()
            .combine(_indexingRefresh) { list, _ -> list }
            .collect { list ->
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
                        firstActionDescription = actions.firstOrNull()?.description,
                        isIndexed = chunkRepository.isIndexed(session.id)
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

    /**
     * Observes all WorkManager jobs tagged [TAG_RAG_INDEXING] and:
     * - Keeps [_isIndexingActive] in sync (true while any job is RUNNING or ENQUEUED)
     * - Increments [_indexingRefresh] when a job succeeds so [observeSessions] re-checks
     *   [ChunkRepository.isIndexed] and updates card state without a Room write.
     */
    private suspend fun observeIndexingWork() {
        WorkManager.getInstance(context)
            .getWorkInfosByTagFlow(TAG_RAG_INDEXING)
            .collect { workInfoList ->
                val active = workInfoList.any {
                    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                }
                _isIndexingActive.value = active
                if (workInfoList.any { it.state == WorkInfo.State.SUCCEEDED }) {
                    _indexingRefresh.value++
                }
            }
    }

    private fun parseActions(json: String): List<ActionItem> =
        runCatching { gson.fromJson<List<ActionItem>>(json, ACTION_LIST_TYPE) }
            .getOrElse { emptyList() }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun markInProgress(id: String) {
        viewModelScope.launch { repository.updateSessionStatus(id, SessionStatus.IN_PROGRESS) }
    }

    fun markDone(id: String) {
        viewModelScope.launch { repository.updateSessionStatus(id, SessionStatus.DONE) }
    }

    fun renameSession(id: String, title: String) {
        viewModelScope.launch {
            if (title.isBlank()) repository.clearUserTitle(id)
            else repository.renameSession(id, title.trim())
        }
    }

    fun archiveSession(id: String) {
        viewModelScope.launch { repository.archiveSession(id) }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch { repository.deleteSession(id) }
    }

    /** Wipes the Q&A chat history for [id] without deleting the session itself. */
    fun clearConversation(id: String) {
        viewModelScope.launch { repository.updateChatHistory(id, emptyList()) }
    }

    /**
     * Forces a re-index of [id] — useful after installing the embedder model or
     * if the document content changed (e.g. rename). Uses [ExistingWorkPolicy.REPLACE]
     * so any in-flight job for this session is cancelled and restarted.
     */
    fun reIndexSession(id: String) {
        IndexingWorker.enqueueReindex(context, id)
    }

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(overdueObserver)
    }
}
