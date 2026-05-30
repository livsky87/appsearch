package com.yoon.js.appsearch.ui.inject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yoon.js.appsearch.data.chunking.TextChunker
import com.yoon.js.appsearch.data.repository.AppSearchRepository
import com.yoon.js.appsearch.domain.model.IndexRequest
import com.yoon.js.appsearch.domain.model.SourceType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class InjectViewModel @Inject constructor(
    private val repository: AppSearchRepository,
    private val chunker: TextChunker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InjectUiState())
    val uiState: StateFlow<InjectUiState> = _uiState.asStateFlow()

    fun onTextChange(text: String) {
        val estimatedChunks = if (text.isBlank()) {
            0
        } else {
            chunker.chunk(text).size
        }
        _uiState.update {
            it.copy(
                text = text,
                estimatedChunkCount = estimatedChunks,
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    fun inject(onSuccess: () -> Unit) {
        val text = _uiState.value.text.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "텍스트를 입력해 주세요.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            repository.indexContent(
                IndexRequest(
                    text = text,
                    sourceType = SourceType.MANUAL,
                    title = text.take(40),
                ),
            ).fold(
                onSuccess = { sourceId ->
                    val chunkCount = repository.getSource(sourceId)?.chunkCount ?: 0
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "$chunkCount 개 청크가 인덱싱되었습니다.",
                        )
                    }
                    onSuccess()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "인덱싱 중 오류가 발생했습니다.",
                        )
                    }
                },
            )
        }
    }
}

data class InjectUiState(
    val text: String = "",
    val estimatedChunkCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)
