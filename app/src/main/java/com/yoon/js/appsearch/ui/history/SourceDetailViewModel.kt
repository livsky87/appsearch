package com.yoon.js.appsearch.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yoon.js.appsearch.data.repository.AppSearchRepository
import com.yoon.js.appsearch.data.web.WebContentValidator
import com.yoon.js.appsearch.domain.model.SourceDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SourceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AppSearchRepository,
) : ViewModel() {

    private val sourceId: Long = checkNotNull(savedStateHandle.get<Long>("sourceId"))

    private val _uiState = MutableStateFlow(SourceDetailUiState())
    val uiState: StateFlow<SourceDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
    }

    fun loadDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.getSourceDetail(sourceId).fold(
                onSuccess = { detail ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = detail,
                            displayTitle = WebContentValidator.sanitizeTitle(
                                title = detail.source.title,
                                url = detail.source.url,
                                previewText = detail.source.previewText,
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "내용을 불러오지 못했습니다.",
                        )
                    }
                },
            )
        }
    }
}

data class SourceDetailUiState(
    val isLoading: Boolean = false,
    val detail: SourceDetail? = null,
    val displayTitle: String = "",
    val errorMessage: String? = null,
)
