package com.yoon.js.appsearch.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yoon.js.appsearch.data.repository.AppSearchRepository
import com.yoon.js.appsearch.domain.model.SourceRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: AppSearchRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadSources()
    }

    fun loadSources() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.listSources().fold(
                onSuccess = { sources ->
                    _uiState.update {
                        it.copy(isLoading = false, sources = sources)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "목록을 불러오지 못했습니다.",
                        )
                    }
                },
            )
        }
    }
}

data class HistoryUiState(
    val isLoading: Boolean = false,
    val sources: List<SourceRecord> = emptyList(),
    val errorMessage: String? = null,
)
