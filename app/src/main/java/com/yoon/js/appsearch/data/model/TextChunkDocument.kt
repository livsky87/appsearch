package com.yoon.js.appsearch.data.model

import androidx.appsearch.app.AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES
import androidx.appsearch.app.AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN
import androidx.appsearch.annotation.Document
import com.yoon.js.appsearch.domain.model.SourceType

@Document
data class TextChunkDocument(
    @Document.Namespace
    val namespace: String,
    @Document.Id
    val id: String,
    @Document.LongProperty
    val sourceId: Long,
    @field:Document.LongProperty
    val chunkIndex: Long,
    @Document.LongProperty
    val sourceType: Long,
    @Document.LongProperty
    val creationTimestampMillis: Long,
    @Document.StringProperty(
        indexingType = INDEXING_TYPE_PREFIXES,
        tokenizerType = TOKENIZER_TYPE_PLAIN,
    )
    val content: String,
) {
    companion object {
        const val NAMESPACE = "text_chunks"
        const val SCHEMA_TYPE = "TextChunkDocument"

        fun create(
            sourceId: Long,
            chunkIndex: Int,
            sourceType: SourceType,
            content: String,
            creationTimestampMillis: Long = System.currentTimeMillis(),
        ): TextChunkDocument = TextChunkDocument(
            namespace = NAMESPACE,
            id = "${sourceId}_$chunkIndex",
            sourceId = sourceId,
            chunkIndex = chunkIndex.toLong(),
            sourceType = sourceType.code,
            creationTimestampMillis = creationTimestampMillis,
            content = content,
        )
    }
}
