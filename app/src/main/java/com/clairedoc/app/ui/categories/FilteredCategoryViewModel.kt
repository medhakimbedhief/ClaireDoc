package com.clairedoc.app.ui.categories

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.clairedoc.app.data.db.toDocumentResult
import com.clairedoc.app.data.model.ActionItem
import com.clairedoc.app.data.model.SessionStatus
import com.clairedoc.app.data.repository.DocumentSessionRepository
import com.clairedoc.app.rag.ChunkRepository
import com.clairedoc.app.rag.TAG_RAG_INDEXING
import com.clairedoc.app.ui.home.HomeSessionItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private val STATUS_ORDER_FC = mapOf(
    SessionStatus.OVERDUE     to 0,
    SessionStatus.UNREAD      to 1,
    SessionStatus.IN_PROGRESS to 2,
    SessionStatus.DONE        to 3
)
private val ISO_DATE_FMT_FC = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val ACTION_LIST_TYPE_FC = object : TypeToken<List<ActionItem>>() {}.type

@HiltViewModel
class FilteredCategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DocumentSessionRepository,
    private val chunkRepository: ChunkRepository,
    private val gson: Gson,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** Decoded document type from the nav argument. */
    val documentType: String = Uri.decode(savedStateHandle.get<String>("documentType") ?: "")

    private val _items = MutableStateFlow<List<HomeSessionItem>>(emptyList())
    val items: StateFlow<List<HomeSessionItem>> = _items.asStateFlow()

    private val _isIndexingActive = MutableStateFlow(false)
    val isIndexingActive: StateFlow<Boolean> = _isIndexingActive.asStateFlow()

    init {
        viewModelScope.launch { observeSessions() }
        viewModelScope.launch { observeIndexing() }
    }

    private suspend fun observeSessions() {
        repository.getAllSessions().collect { list ->
            val today = LocalDate.now()
            val filtered = list.filter { it.documentType == documentType && !it.isArchived }
            val built = filtered.map { session ->
                val actions = parseActions(session.actionsJson)
                val deadlineDays = actions
                    .mapNotNull { it.deadline }
                    .mapNotNull { runCatching { LocalDate.parse(it, ISO_DATE_FMT_FC) }.getOrNull() }
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
                    { STATUS_ORDER_FC[it.session.status] ?: 99 },
                    { -it.session.createdAt }
                )
            )
            _items.value = built
        }
    }

    private suspend fun observeIndexing() {
        WorkManager.getInstance(context)
            .getWorkInfosByTagFlow(TAG_RAG_INDEXING)
            .collect { workInfoList ->
                _isIndexingActive.value = workInfoList.any {
                    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                }
            }
    }

    private fun parseActions(json: String): List<ActionItem> =
        runCatching { gson.fromJson<List<ActionItem>>(json, ACTION_LIST_TYPE_FC) }
            .getOrElse { emptyList() }
}
