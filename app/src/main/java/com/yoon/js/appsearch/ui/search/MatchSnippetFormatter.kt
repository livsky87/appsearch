package com.yoon.js.appsearch.ui.search

import com.yoon.js.appsearch.domain.model.ChunkSearchResult
import com.yoon.js.appsearch.domain.model.MatchHighlight
import com.yoon.js.appsearch.domain.model.TextRange

object MatchSnippetFormatter {

    const val MAX_SNIPPETS = 3
    const val MAX_SNIPPET_LENGTH = 120
    private const val CONTEXT_CHARS = 24

    data class DisplaySnippet(
        val text: String,
        val ranges: List<TextRange>,
    )

    fun formatSnippets(
        result: ChunkSearchResult,
        query: String,
    ): List<DisplaySnippet> {
        val fromMatches = result.matches
            .mapNotNull(::fromMatchHighlight)
            .distinctBy { it.text }

        if (fromMatches.isNotEmpty()) {
            return fromMatches.take(MAX_SNIPPETS)
        }

        val highlight = QueryHighlightFormatter.buildHighlight(
            content = result.content,
            query = query,
        )
        if (highlight.ranges.isEmpty()) {
            return listOf(
                DisplaySnippet(
                    text = truncate(result.content, MAX_SNIPPET_LENGTH),
                    ranges = emptyList(),
                ),
            )
        }

        return highlight.ranges
            .map { range -> snippetAroundRange(highlight.text, range) }
            .distinctBy { it.text }
            .take(MAX_SNIPPETS)
    }

    fun formatExtraMatchCount(
        result: ChunkSearchResult,
        query: String,
    ): Int {
        val total = when {
            result.matches.isNotEmpty() -> result.matches.size
            else -> {
                val highlight = QueryHighlightFormatter.buildHighlight(result.content, query)
                highlight.ranges.size.coerceAtLeast(1)
            }
        }
        return (total - MAX_SNIPPETS).coerceAtLeast(0)
    }

    private fun fromMatchHighlight(match: MatchHighlight): DisplaySnippet? {
        val snippet = normalizeWhitespace(match.snippet)
        if (snippet.isBlank()) return null

        val truncated = truncate(snippet, MAX_SNIPPET_LENGTH)
        val highlight = QueryHighlightFormatter.buildHighlight(
            content = truncated,
            query = match.matchedTerm,
        )

        return DisplaySnippet(text = truncated, ranges = highlight.ranges)
    }

    private fun snippetAroundRange(content: String, range: TextRange): DisplaySnippet {
        val start = range.start.coerceIn(0, content.length)
        val end = range.end.coerceIn(start, content.length)
        val windowStart = (start - CONTEXT_CHARS).coerceAtLeast(0)
        val windowEnd = (end + CONTEXT_CHARS).coerceAtMost(content.length)
        val raw = content.substring(windowStart, windowEnd)
        val prefix = if (windowStart > 0) "…" else ""
        val suffix = if (windowEnd < content.length) "…" else ""
        val text = truncate(prefix + raw + suffix, MAX_SNIPPET_LENGTH)
        val highlightStart = prefix.length + (start - windowStart)
        val highlightEnd = prefix.length + (end - windowStart)
        val ranges = listOf(
            TextRange(
                start = highlightStart.coerceIn(0, text.length),
                end = highlightEnd.coerceIn(highlightStart, text.length),
            ),
        )
        return DisplaySnippet(text = text, ranges = ranges)
    }

    private fun truncate(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text
        if (maxLength <= 1) return "…"
        return text.take(maxLength - 1) + "…"
    }

    private fun normalizeWhitespace(text: String): String =
        text.replace(Regex("\\s+"), " ").trim()
}
