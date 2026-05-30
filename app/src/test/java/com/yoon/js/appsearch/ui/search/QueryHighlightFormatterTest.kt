package com.yoon.js.appsearch.ui.search

import com.yoon.js.appsearch.domain.model.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryHighlightFormatterTest {

    @Test
    fun findAllOccurrences_findsExactMatches() {
        val ranges = QueryHighlightFormatter.findAllOccurrences("아침에는 밥을 먹는다.", "아침")
        assertEquals(1, ranges.size)
        assertEquals(0, ranges.first().start)
        assertEquals(2, ranges.first().end)
    }

    @Test
    fun buildHighlight_highlightsQueryInFullContent() {
        val segment = QueryHighlightFormatter.buildHighlight(
            content = "아침에는 밥을 먹는다.",
            query = "아침",
        )
        assertEquals("아침에는 밥을 먹는다.", segment.text)
        assertEquals(1, segment.ranges.size)
        assertEquals("아침", segment.text.substring(segment.ranges.first().start, segment.ranges.first().end))
    }

    @Test
    fun buildHighlight_supportsMultipleTerms() {
        val segment = QueryHighlightFormatter.buildHighlight(
            content = "아침에는 점심에는 저녁에는",
            query = "아침 저녁",
        )
        assertTrue(segment.ranges.size >= 2)
    }
}
