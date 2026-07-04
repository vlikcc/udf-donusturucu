package com.velikececi.udfdonusturucu.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velikececi.udfdonusturucu.data.ConversionRecord
import com.velikececi.udfdonusturucu.data.HistoryRepository
import com.velikececi.udfdonusturucu.data.LimitRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MainUiState(
    val isPremium: Boolean = false,
    val remainingConversions: Int = LimitRepository.MAX_FREE_CONVERSIONS,
    val recentConversions: List<ConversionRecord> = emptyList(),
) {
    val canConvert: Boolean get() = isPremium || remainingConversions > 0
}

/**
 * Günlük limit kartı ve "son dönüşümler" listesi için salt-okunur durumu sağlar. Seçilen
 * dosyalar / dönüşüm yönü / format artık [com.velikececi.udfdonusturucu.ui.conversion.ConversionFlowViewModel]'de
 * tutuluyor (ana ekran → dönüşüm → sonuç arasında paylaşılması gerektiğinden).
 */
class MainViewModel(
    private val limitRepository: LimitRepository,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    val uiState: StateFlow<MainUiState> = combine(
        limitRepository.state,
        historyRepository.records,
    ) { limitState, records ->
        MainUiState(
            isPremium = limitState.isPremium,
            remainingConversions = limitState.remainingConversions,
            recentConversions = historyRepository.availableRecords(limitState.isPremium).take(5),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )
}
