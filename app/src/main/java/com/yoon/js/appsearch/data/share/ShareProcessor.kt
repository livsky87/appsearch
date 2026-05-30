package com.yoon.js.appsearch.data.share

import com.yoon.js.appsearch.data.repository.AppSearchRepository
import com.yoon.js.appsearch.data.web.WebContentExtractor
import com.yoon.js.appsearch.data.web.WebExtractRequest
import com.yoon.js.appsearch.domain.model.IndexRequest
import com.yoon.js.appsearch.domain.model.SourceType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareProcessor @Inject constructor(
    private val webContentExtractor: WebContentExtractor,
    private val repository: AppSearchRepository,
) {
    suspend fun process(payload: SharePayload): ShareProcessResult {
        ShareFlowLogger.d("ShareProcessor", "process start type=${payload.sourceType}")
        return try {
            val indexRequest = when (payload.sourceType) {
                SourceType.SHARE_WEB -> {
                    ShareFlowLogger.d(
                        "ShareProcessor",
                        "web extract url=${ShareFlowLogger.preview(payload.url, 100)} fetchUrls=${payload.fetchUrls.size}",
                    )
                    val webContent = webContentExtractor.extract(
                        WebExtractRequest(
                            url = payload.url,
                            fetchUrls = payload.fetchUrls,
                            sharedHtml = payload.html,
                            titleHint = payload.titleHint,
                            descriptionHint = payload.descriptionHint,
                            imageUrlHint = payload.imageUrlHint,
                        ),
                    )
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

            repository.indexContent(indexRequest).fold(
                onSuccess = { sourceId ->
                    if (sourceId == 0L) {
                        ShareProcessResult.Failure("저장할 내용이 없습니다")
                    } else {
                        ShareFlowLogger.d("ShareProcessor", "index OK sourceId=$sourceId")
                        ShareProcessResult.Success(sourceId)
                    }
                },
                onFailure = { error ->
                    ShareFlowLogger.e("ShareProcessor", "index failed", error)
                    ShareProcessResult.Failure(error.message ?: "인덱싱 실패")
                },
            )
        } catch (e: Exception) {
            ShareFlowLogger.e("ShareProcessor", "process exception", e)
            ShareProcessResult.Failure(e.message ?: "처리 실패")
        }
    }
}
