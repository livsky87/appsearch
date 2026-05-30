package com.yoon.js.appsearch.domain.model

data class IndexRequest(
    val text: String,
    val sourceType: SourceType,
    val title: String = "",
    val url: String = "",
    val imageUrl: String = "",
)
