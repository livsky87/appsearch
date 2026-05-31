package com.yoon.js.appsearch.ui.search

import com.yoon.js.appsearch.domain.model.ChunkSearchResult

data class SearchResultSummary(
    val resultCount: Int,
    val sourceCount: Int,
    val topScore: Double?,
    val averageScore: Double?,
)

object SearchResultSummaryFormatter {

    fun fromResults(results: List<ChunkSearchResult>): SearchResultSummary {
        if (results.isEmpty()) {
            return SearchResultSummary(
                resultCount = 0,
                sourceCount = 0,
                topScore = null,
                averageScore = null,
            )
        }

        val scores = results.map { it.relevanceScore }
        return SearchResultSummary(
            resultCount = results.size,
            sourceCount = results.map { it.sourceId }.distinct().size,
            topScore = scores.maxOrNull(),
            averageScore = scores.average(),
        )
    }

    fun format(summary: SearchResultSummary, isLoading: Boolean): String? {
        if (isLoading) return "검색 중…"

        return when {
            summary.resultCount == 0 -> "검색 결과 0건"
            else -> buildString {
                append("검색 결과 ${summary.resultCount}건")
                append(" · 출처 ${summary.sourceCount}건")
                summary.topScore?.let { append(" · 최고 점수 ${formatScore(it)}") }
                summary.averageScore?.let { append(" · 평균 점수 ${formatScore(it)}") }
            }
        }
    }

    private fun formatScore(score: Double): String = String.format("%.2f", score)
}
