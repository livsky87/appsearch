package com.yoon.js.appsearch.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yoon.js.appsearch.data.repository.AppSearchRepository
import com.yoon.js.appsearch.data.share.ShareCoordinator
import com.yoon.js.appsearch.data.share.SharePayload
import com.yoon.js.appsearch.data.web.WebContentExtractor
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
class ShareProcessingViewModel @Inject constructor(
    private val shareCoordinator: ShareCoordinator,
    private val webContentExtractor: WebContentExtractor,
    private val repository: AppSearchRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareProcessingUiState())
    val uiState: StateFlow<ShareProcessingUiState> = _uiState.asStateFlow()

    private var started = false

    fun process(onComplete: (ShareProcessingResult) -> Unit) {
        if (started) return
        val payload = shareCoordinator.consume() ?: run {
            onComplete(ShareProcessingResult.NoShare)
            return
        }
        started = true
        processPayload(payload, onComplete)
    }

    fun reset() {
        started = false
    }

    private fun processPayload(payload: SharePayload, onComplete: (ShareProcessingResult) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = "공유 내용 처리 중...") }
            try {
                val indexRequest = when (payload.sourceType) {
                    SourceType.SHARE_WEB -> {
                        _uiState.update { it.copy(message = "페이지 추출 중...") }
                        val webContent = webContentExtractor.extract(payload.url)
                        IndexRequest(
                            text = webContent.bodyText,
                            sourceType = SourceType.SHARE_WEB,
                            title = webContent.title.ifBlank { payload.titleHint },
                            url = webContent.finalUrl.ifBlank { payload.url },
                            imageUrl = webContent.imageUrl.ifBlank { payload.imageUrlHint },
                        )
                    }
                    SourceType.SHARE_TEXT -> {
                        IndexRequest(
                            text = payload.text,
                            sourceType = SourceType.SHARE_TEXT,
                            title = payload.text.take(40),
                        )
                    }
                    SourceType.MANUAL -> {
                        IndexRequest(
                            text = payload.text,
                            sourceType = SourceType.MANUAL,
                        )
                    }
                }

                _uiState.update { it.copy(message = "인덱싱 중...") }
                repository.indexContent(indexRequest).fold(
                    onSuccess = { sourceId ->
                        _uiState.update {
                            it.copy(isLoading = false, message = "인덱싱 완료")
                        }
                        onComplete(ShareProcessingResult.Success(sourceId))
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "인덱싱 실패",
                            )
                        }
                        onComplete(ShareProcessingResult.Failure(error.message))
                    },
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "처리 실패",
                    )
                }
                onComplete(ShareProcessingResult.Failure(e.message))
            }
        }
    }
}

data class ShareProcessingUiState(
    val isLoading: Boolean = false,
    val message: String = "",
    val errorMessage: String? = null,
)

sealed interface ShareProcessingResult {
    data object NoShare : ShareProcessingResult
    data class Success(val sourceId: Long) : ShareProcessingResult
    data class Failure(val message: String?) : ShareProcessingResult
}
