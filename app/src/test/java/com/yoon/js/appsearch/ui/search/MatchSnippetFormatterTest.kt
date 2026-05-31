package com.yoon.js.appsearch.ui.search

import com.yoon.js.appsearch.domain.model.ChunkSearchResult
import com.yoon.js.appsearch.domain.model.MatchHighlight
import com.yoon.js.appsearch.domain.model.SourceType
import com.yoon.js.appsearch.domain.model.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchSnippetFormatterTest {

    @Test
    fun formatSnippets_usesAppSearchMatchSnippet() {
        val longSnippet = "아침에 일어나서 " + "가".repeat(200) + " 밥을 먹었다."
        val result = sampleResult(
            content = longSnippet,
            matches = listOf(
                MatchHighlight(
                    propertyPath = "content",
                    snippet = longSnippet,
                    matchedTerm = "아침",
                    ranges = listOf(TextRange(0, 2)),
                ),
            ),
        )

        val snippets = MatchSnippetFormatter.formatSnippets(result, query = "아침")

        assertEquals(1, snippets.size)
        assertTrue(snippets.first().text.length <= MatchSnippetFormatter.MAX_SNIPPET_LENGTH)
        assertTrue(snippets.first().text.contains("아침"))
    }

    @Test
    fun formatSnippets_limitsSnippetCount() {
        val result = sampleResult(
            content = "아침 점심 저녁",
            matches = listOf(
                match("아침", 0, 2),
                match("점심", 3, 5),
                match("저녁", 6, 8),
                match("밤", 9, 10),
            ),
        )

        val snippets = MatchSnippetFormatter.formatSnippets(result, query = "아침")

        assertEquals(MatchSnippetFormatter.MAX_SNIPPETS, snippets.size)
        assertEquals(1, MatchSnippetFormatter.formatExtraMatchCount(result, query = "아침"))
    }

    @Test
    fun formatSnippets_fallsBackToContentContext() {
        val content = "앞부분 " + "중간".repeat(80) + " 아침에는 밥을 먹는다. 뒷부분"
        val result = sampleResult(content = content, matches = emptyList())

        val snippets = MatchSnippetFormatter.formatSnippets(result, query = "아침")

        assertEquals(1, snippets.size)
        assertTrue(snippets.first().text.contains("아침"))
        assertTrue(snippets.first().text.length <= MatchSnippetFormatter.MAX_SNIPPET_LENGTH)
    }

    private fun match(term: String, start: Int, end: Int) = MatchHighlight(
        propertyPath = "content",
        snippet = term,
        matchedTerm = term,
        ranges = listOf(TextRange(start, end)),
    )

    private fun sampleResult(
        content: String,
        matches: List<MatchHighlight>,
    ) = ChunkSearchResult(
        id = "1",
        sourceId = 1L,
        chunkIndex = 0,
        content = content,
        relevanceScore = 1.0,
        sourceType = SourceType.MANUAL,
        sourceTitle = "제목",
        sourceUrl = "",
        matches = matches,
    )
}
