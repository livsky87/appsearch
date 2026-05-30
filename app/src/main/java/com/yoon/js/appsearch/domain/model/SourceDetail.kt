package com.yoon.js.appsearch.domain.model

data class SourceDetail(
    val source: SourceRecord,
    val fullText: String,
    val chunks: List<String>,
)
