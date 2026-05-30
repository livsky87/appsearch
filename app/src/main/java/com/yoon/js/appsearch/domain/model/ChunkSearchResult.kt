package com.yoon.js.appsearch.domain.model

data class ChunkSearchResult(
    val id: String,
    val sourceId: Long,
    val chunkIndex: Int,
    val content: String,
    val relevanceScore: Double,
    val sourceType: SourceType,
    val sourceTitle: String,
    val sourceUrl: String,
    val matches: List<MatchHighlight>,
)
