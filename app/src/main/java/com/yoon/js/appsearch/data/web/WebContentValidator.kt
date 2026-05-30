package com.yoon.js.appsearch.data.web

import java.net.URI

object WebContentValidator {
    private val ERROR_TITLE_KEYWORDS = listOf(
        "웹페이지를 사용할 수 없",
        "페이지를 사용할 수 없",
        "사이트에 연결할 수 없",
        "this site can't be reached",
        "web page not available",
    )

    private val ERROR_BODY_KEYWORDS = listOf(
        "net::err_",
        "err_connection",
        "err_name_not_resolved",
        "err_ssl",
        "err_timed_out",
        "웹페이지를 사용할 수 없",
        "사이트에 연결할 수 없",
        "this site can't be reached",
        "web page not available",
    )

    fun isValid(title: String, bodyText: String): Boolean {
        val normalizedBody = bodyText.trim()
        if (normalizedBody.length < MIN_BODY_LENGTH) return false
        return !isErrorBody(normalizedBody)
    }

    fun isErrorTitle(title: String): Boolean {
        val normalized = title.trim()
        if (normalized.isBlank()) return false
        val lower = normalized.lowercase()
        return ERROR_TITLE_KEYWORDS.any { lower.contains(it) }
    }

    fun isErrorBody(bodyText: String): Boolean {
        val normalized = bodyText.trim()
        if (normalized.length < MIN_BODY_LENGTH) return true
        val lower = normalized.lowercase()
        val hasErrorKeyword = ERROR_BODY_KEYWORDS.any { lower.contains(it) }
        if (!hasErrorKeyword) return false
        return normalized.length < ERROR_BODY_MAX_LENGTH
    }

    fun sanitizeTitle(title: String, url: String, previewText: String): String {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isNotBlank() && !isErrorTitle(trimmedTitle)) {
            return trimmedTitle
        }
        val host = extractDisplayHost(url)
        if (host.isNotBlank()) return host
        return previewText.trim().take(40).ifBlank { "Untitled" }
    }

    fun extractDisplayHost(url: String): String {
        if (url.isBlank()) return ""
        return runCatching {
            URI(url).host?.removePrefix("www.").orEmpty()
        }.getOrDefault("")
    }

    private const val MIN_BODY_LENGTH = 30
    private const val ERROR_BODY_MAX_LENGTH = 400
}
