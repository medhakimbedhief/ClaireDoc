package com.clairedoc.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clairedoc.app.data.model.UrgencyLevel
import com.clairedoc.app.data.repository.DocumentSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CategorySummary(
    val documentType: String,
    val count: Int,
    val highestUrgency: UrgencyLevel
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val sessionRepository: DocumentSessionRepository
) : ViewModel() {

    val categories: StateFlow<List<CategorySummary>> =
        sessionRepository.getAllSessions()
            .map { sessions ->
                sessions
                    .filter { !it.isArchived }
                    .groupBy { it.documentType }
                    .map { (type, docs) ->
                        CategorySummary(
                            documentType = type,
                            count = docs.size,
                            highestUrgency = docs.maxByOrNull {
                                when (it.urgencyLevel) {
                                    "RED"    -> 2
                                    "YELLOW" -> 1
                                    else     -> 0
                                }
                            }?.urgencyLevel?.toUrgencyLevel() ?: UrgencyLevel.GREEN
                        )
                    }
                    .sortedByDescending { it.count }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
