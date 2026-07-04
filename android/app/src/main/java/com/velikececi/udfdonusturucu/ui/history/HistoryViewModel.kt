package com.velikececi.udfdonusturucu.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velikececi.udfdonusturucu.data.ConversionRecord
import com.velikececi.udfdonusturucu.data.HistoryRepository
import com.velikececi.udfdonusturucu.data.LimitRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val available: List<ConversionRecord> = emptyList(),
    val unavailable: List<ConversionRecord> = emptyList(),
)

class HistoryViewModel(
    private val historyRepository: HistoryRepository,
    limitRepository: LimitRepository,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = combine(
        historyRepository.records,
        limitRepository.state,
    ) { _, limitState ->
        val recent = historyRepository.recentRecords(limitState.isPremium)
        val available = historyRepository.availableRecords(limitState.isPremium)
        val availableIds = available.map { it.id }.toSet()
        HistoryUiState(
            available = available,
            unavailable = recent.filterNot { it.id in availableIds },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    fun delete(record: ConversionRecord) {
        viewModelScope.launch { historyRepository.deleteRecord(record) }
    }

    fun clearAll() {
        viewModelScope.launch { historyRepository.clearHistory() }
    }
}
