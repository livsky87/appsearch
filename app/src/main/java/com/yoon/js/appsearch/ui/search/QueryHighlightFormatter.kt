package com.yoon.js.appsearch.ui.search

import com.yoon.js.appsearch.domain.model.MatchHighlight
import com.yoon.js.appsearch.domain.model.TextRange

object QueryHighlightFormatter {

    data class HighlightSegment(
        val text: String,
        val ranges: List<TextRange>,
        val matchedTerms: List<String>,
    )

    fun buildHighlight(
        content: String,
        query: String,
        matches: List<MatchHighlight>,
    ): HighlightSegment {
        val matchRanges = matches.flatMap { it.ranges }
        val matchTerms = matches.mapNotNull { it.matchedTerm.takeIf(String::isNotBlank) }.distinct()

        if (matchRanges.isNotEmpty()) {
            val displayText = matches.firstOrNull()?.snippet?.takeIf { it.isNotBlank() } ?: content
            val snippetRanges = matches.firstOrNull()?.ranges.orEmpty()
            return HighlightSegment(
                text = displayText,
                ranges = snippetRanges,
                matchedTerms = matchTerms,
            )
        }

        val queryTerms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (queryTerms.isEmpty()) {
            return HighlightSegment(content, emptyList(), emptyList())
        }

        val ranges = mutableListOf<TextRange>()
        val foundTerms = mutableListOf<String>()
        for (term in queryTerms) {
            findAllOccurrences(content, term).forEach { range ->
                ranges.add(range)
                foundTerms.add(content.substring(range.start, range.end))
            }
        }

        return HighlightSegment(
            text = content,
            ranges = mergeOverlappingRanges(ranges),
            matchedTerms = foundTerms.distinct(),
        )
    }

    fun findAllOccurrences(text: String, term: String): List<TextRange> {
        if (term.isBlank()) return emptyList()
        val ranges = mutableListOf<TextRange>()
        var startIndex = 0
        while (startIndex < text.length) {
            val index = text.indexOf(term, startIndex, ignoreCase = true)
            if (index < 0) break
            ranges.add(TextRange(index, index + term.length))
            startIndex = index + term.length
        }
        return ranges
    }

    private fun mergeOverlappingRanges(ranges: List<TextRange>): List<TextRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.start }
        val merged = mutableListOf<TextRange>()
        var current = sorted.first()
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.start <= current.end) {
                current = TextRange(current.start, maxOf(current.end, next.end))
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }
}
