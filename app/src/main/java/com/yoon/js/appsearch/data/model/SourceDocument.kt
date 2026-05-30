package com.yoon.js.appsearch.data.model

import androidx.appsearch.annotation.Document
import com.yoon.js.appsearch.domain.model.SourceType

@Document
data class SourceDocument(
    @Document.Namespace
    val namespace: String,
    @Document.Id
    val id: String,
    @Document.LongProperty
    val sourceType: Long,
    @Document.StringProperty
    val title: String,
    @Document.StringProperty
    val url: String,
    @Document.StringProperty
    val imageUrl: String,
    @Document.StringProperty
    val previewText: String,
    @Document.LongProperty
    val chunkCount: Long,
    @Document.LongProperty
    val creationTimestampMillis: Long,
) {
    companion object {
        const val NAMESPACE = "sources"
        const val SCHEMA_TYPE = "SourceDocument"
        const val PREVIEW_MAX_LENGTH = 120

        fun create(
            sourceId: Long,
            sourceType: SourceType,
            title: String,
            url: String,
            imageUrl: String,
            previewText: String,
            chunkCount: Int,
            creationTimestampMillis: Long = System.currentTimeMillis(),
        ): SourceDocument {
            val trimmedPreview = previewText.trim().take(PREVIEW_MAX_LENGTH)
            return SourceDocument(
                namespace = NAMESPACE,
                id = sourceId.toString(),
                sourceType = sourceType.code,
                title = title.ifBlank { trimmedPreview.take(40).ifBlank { "Untitled" } },
                url = url,
                imageUrl = imageUrl,
                previewText = trimmedPreview,
                chunkCount = chunkCount.toLong(),
                creationTimestampMillis = creationTimestampMillis,
            )
        }
    }
}
