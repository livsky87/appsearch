package com.yoon.js.appsearch.ui.search

import com.yoon.js.appsearch.domain.model.ChunkSearchResult
import com.yoon.js.appsearch.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchResultSummaryFormatterTest {

    @Test
    fun fromResults_empty_returnsZeroCounts() {
        val summary = SearchResultSummaryFormatter.fromResults(emptyList())

        assertEquals(0, summary.resultCount)
        assertEquals(0, summary.sourceCount)
        assertNull(summary.topScore)
        assertNull(summary.averageScore)
    }

    @Test
    fun fromResults_countsDistinctSourcesAndScores() {
        val results = listOf(
            chunk(sourceId = 1L, score = 0.5),
            chunk(sourceId = 1L, score = 1.0),
            chunk(sourceId = 2L, score = 0.25),
        )

        val summary = SearchResultSummaryFormatter.fromResults(results)

        assertEquals(3, summary.resultCount)
        assertEquals(2, summary.sourceCount)
        assertEquals(1.0, summary.topScore!!, 0.001)
        assertEquals(0.583, summary.averageScore!!, 0.001)
    }

    @Test
    fun format_loading_showsLoadingMessage() {
        val text = SearchResultSummaryFormatter.format(
            SearchResultSummary(0, 0, null, null),
            isLoading = true,
        )

        assertEquals("검색 중…", text)
    }

    @Test
    fun format_noResults_showsZeroCount() {
        val text = SearchResultSummaryFormatter.format(
            SearchResultSummary(0, 0, null, null),
            isLoading = false,
        )

        assertEquals("검색 결과 0건", text)
    }

    @Test
    fun format_withResults_includesMetrics() {
        val text = SearchResultSummaryFormatter.format(
            SearchResultSummary(
                resultCount = 2,
                sourceCount = 1,
                topScore = 1.234,
                averageScore = 0.987,
            ),
            isLoading = false,
        )

        assertEquals(
            "검색 결과 2건 · 출처 1건 · 최고 점수 1.23 · 평균 점수 0.99",
            text,
        )
    }

    private fun chunk(sourceId: Long, score: Double) = ChunkSearchResult(
        id = "${sourceId}_0",
        sourceId = sourceId,
        chunkIndex = 0,
        content = "sample",
        relevanceScore = score,
        sourceType = SourceType.MANUAL,
        sourceTitle = "title",
        sourceUrl = "",
        matches = emptyList(),
    )
}
