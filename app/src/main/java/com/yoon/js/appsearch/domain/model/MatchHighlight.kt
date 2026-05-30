package com.yoon.js.appsearch.domain.model

data class TextRange(
    val start: Int,
    val end: Int,
)

data class MatchHighlight(
    val propertyPath: String,
    val snippet: String,
    val matchedTerm: String,
    val ranges: List<TextRange>,
)
