package com.yoon.js.appsearch.domain.model

data class SourceRecord(
    val sourceId: Long,
    val sourceType: SourceType,
    val title: String,
    val url: String,
    val imageUrl: String,
    val previewText: String,
    val creationTimestampMillis: Long,
    val chunkCount: Int = 0,
)
