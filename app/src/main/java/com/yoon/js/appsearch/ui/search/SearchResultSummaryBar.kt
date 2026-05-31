package com.yoon.js.appsearch.ui.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yoon.js.appsearch.domain.model.ChunkSearchResult

@Composable
fun SearchResultSummaryBar(
    results: List<ChunkSearchResult>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val summary = SearchResultSummaryFormatter.fromResults(results)
    val text = SearchResultSummaryFormatter.format(summary, isLoading) ?: return

    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
