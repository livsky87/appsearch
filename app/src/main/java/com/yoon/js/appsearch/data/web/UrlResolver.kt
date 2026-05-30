package com.yoon.js.appsearch.data.web

import java.net.URI
import java.net.URLDecoder

object UrlResolver {
    fun normalize(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ""
        return unwrapBridgeUrl(trimmed)
    }

    fun unwrapBridgeUrl(url: String): String {
        var current = url.trim()
        repeat(3) {
            val uri = runCatching { URI(current) }.getOrNull() ?: return current
            val host = uri.host.orEmpty().lowercase()
            if (!host.contains("link.naver.com")) return current

            val embeddedUrl = extractQueryParam(current, "url")
            if (embeddedUrl.isBlank()) return current
            current = URLDecoder.decode(embeddedUrl, Charsets.UTF_8.name())
        }
        return current
    }

    fun selectBestFetchUrl(candidates: Iterable<String>): String {
        return candidates
            .map { normalize(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .maxByOrNull { articleUrlScore(it) }
            .orEmpty()
    }

    fun orderedFetchUrls(candidates: Iterable<String>): List<String> {
        return candidates
            .map { normalize(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedByDescending { articleUrlScore(it) }
    }

    fun isDirectArticleUrl(url: String): Boolean {
        val normalized = normalize(url)
        if (normalized.isBlank()) return false
        val host = runCatching { URI(normalized).host.orEmpty().lowercase() }.getOrDefault("")
        if (host.contains("link.naver.com") || host == "naver.me" || host.endsWith(".naver.me")) {
            return false
        }
        return host.contains("news.naver.com") ||
            host.contains("n.news.naver.com") ||
            (!host.contains("naver.com") && !host.contains("naver.me"))
    }

    fun isIntermediateUrl(url: String): Boolean {
        val host = runCatching { URI(url.trim()).host.orEmpty().lowercase() }.getOrDefault("")
        return host.contains("link.naver.com") || host == "naver.me" || host.endsWith(".naver.me")
    }

    fun articleUrlScore(url: String): Int {
        val normalized = normalize(url)
        val host = runCatching { URI(normalized).host.orEmpty().lowercase() }.getOrDefault("")
        return when {
            host.contains("n.news.naver.com") -> 100
            host.contains("news.naver.com") -> 90
            host.contains("naver.com") -> 40
            host.contains("link.naver.com") -> 10
            host.contains("naver.me") -> 5
            else -> 50
        }
    }

    private fun extractQueryParam(url: String, key: String): String {
        val query = runCatching { URI(url).rawQuery }.getOrNull().orEmpty()
        if (query.isBlank()) return ""
        return query.split("&").firstNotNullOfOrNull { param ->
            val parts = param.split("=", limit = 2)
            if (parts.firstOrNull() == key) parts.getOrNull(1).orEmpty() else null
        }.orEmpty()
    }
}
