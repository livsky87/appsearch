package com.yoon.js.appsearch.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yoon.js.appsearch.data.repository.AppSearchRepository
import com.yoon.js.appsearch.domain.model.ChunkSearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: AppSearchRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        observeQuery()
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, errorMessage = null) }
        queryFlow.value = query
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeQuery() {
        viewModelScope.launch {
            queryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    flow {
                        val trimmed = query.trim()
                        if (trimmed.isEmpty()) {
                            emit(SearchUiState(query = query, isLoading = false, results = emptyList()))
                            return@flow
                        }
                        emit(SearchUiState(query = query, isLoading = true, results = emptyList()))
                        val result = repository.search(trimmed)
                        result.fold(
                            onSuccess = { results ->
                                emit(
                                    SearchUiState(
                                        query = query,
                                        isLoading = false,
                                        results = results,
                                    ),
                                )
                            },
                            onFailure = { error ->
                                emit(
                                    SearchUiState(
                                        query = query,
                                        isLoading = false,
                                        results = emptyList(),
                                        errorMessage = error.message ?: "검색 중 오류가 발생했습니다.",
                                    ),
                                )
                            },
                        )
                    }
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<ChunkSearchResult> = emptyList(),
    val errorMessage: String? = null,
)
