package com.yoon.js.appsearch.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.yoon.js.appsearch.domain.model.ChunkSearchResult
import com.yoon.js.appsearch.domain.model.SourceType

@Composable
fun SearchResultCard(
    result: ChunkSearchResult,
    query: String,
    modifier: Modifier = Modifier,
) {
    val highlight = QueryHighlightFormatter.buildHighlight(
        content = result.content,
        query = query,
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "청크 #${result.chunkIndex + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (result.sourceTitle.isNotBlank()) {
                        Text(
                            text = result.sourceTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = "Score ${String.format("%.2f", result.relevanceScore)}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            SourceTypeBadge(sourceType = result.sourceType, url = result.sourceUrl)

            HighlightedText(
                text = highlight.text,
                ranges = highlight.ranges,
            )

            highlight.ranges.forEachIndexed { index, range ->
                val matchedText = result.content.substring(
                    range.start.coerceIn(0, result.content.length),
                    range.end.coerceIn(range.start, result.content.length),
                )
                Text(
                    text = "매칭 ${index + 1}: \"$matchedText\" @ content (pos ${range.start}-${range.end})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SourceTypeBadge(sourceType: SourceType, url: String) {
    val label = when (sourceType) {
        SourceType.MANUAL -> "직접 입력"
        SourceType.SHARE_TEXT -> "공유 텍스트"
        SourceType.SHARE_WEB -> "공유 웹"
    }
    val detail = if (url.isNotBlank()) " · $url" else ""
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = "$label$detail",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun HighlightedText(
    text: String,
    ranges: List<com.yoon.js.appsearch.domain.model.TextRange>,
) {
    val annotated = buildAnnotatedString {
        if (ranges.isEmpty()) {
            append(text)
            return@buildAnnotatedString
        }

        val sortedRanges = ranges.sortedBy { it.start }
        var cursor = 0
        for (range in sortedRanges) {
            val start = range.start.coerceIn(0, text.length)
            val end = range.end.coerceIn(start, text.length)
            if (cursor < start) {
                append(text.substring(cursor, start))
            }
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    background = MaterialTheme.colorScheme.secondaryContainer,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                append(text.substring(start, end))
            }
            cursor = end
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
    )
}
