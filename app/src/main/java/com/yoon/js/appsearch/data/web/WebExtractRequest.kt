package com.yoon.js.appsearch.data.web

data class WebExtractRequest(
    val url: String,
    val fetchUrls: List<String> = emptyList(),
    val sharedHtml: String = "",
    val titleHint: String = "",
    val descriptionHint: String = "",
    val imageUrlHint: String = "",
)
