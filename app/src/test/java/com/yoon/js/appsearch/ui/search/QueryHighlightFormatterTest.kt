package com.yoon.js.appsearch.ui.search

import com.yoon.js.appsearch.domain.model.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryHighlightFormatterTest {

    @Test
    fun findAllOccurrences_findsCaseInsensitiveMatches() {
        val ranges = QueryHighlightFormatter.findAllOccurrences("Hello hello HELLO", "hello")
        assertEquals(3, ranges.size)
    }

    @Test
    fun buildHighlight_usesQueryTermsWhenNoMatchInfo() {
        val segment = QueryHighlightFormatter.buildHighlight(
            content = "AppSearch는 검색 엔진입니다.",
            query = "검색",
            matches = emptyList(),
        )
        assertTrue(segment.ranges.isNotEmpty())
        assertEquals("검색", segment.text.substring(segment.ranges.first().start, segment.ranges.first().end))
    }
}
