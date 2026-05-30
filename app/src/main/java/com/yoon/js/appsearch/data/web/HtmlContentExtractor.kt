package com.yoon.js.appsearch.data.web

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class HtmlContentExtractor @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    fun resolveFinalUrl(url: String): String {
        var current = UrlResolver.normalize(url)
        if (UrlResolver.isDirectArticleUrl(current)) return current

        repeat(MAX_REDIRECTS) {
            val request = Request.Builder()
                .url(current)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                response.body?.close()
                val next = UrlResolver.normalize(response.request.url.toString())
                if (UrlResolver.isDirectArticleUrl(next)) return next
                if (next == current) return current
                current = next
            }
        }
        return UrlResolver.normalize(current)
    }

    fun extract(url: String): WebContent {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            val html = response.body?.string().orEmpty()
            val finalUrl = UrlResolver.normalize(response.request.url.toString())
            return parseHtml(html, url, finalUrl)
        }
    }

    fun parseHtml(html: String, originalUrl: String, finalUrl: String = originalUrl): WebContent {
        val document = Jsoup.parse(html, finalUrl)
        val title = extractTitle(document)
        val bodyText = extractBodyText(document)
        val imageUrl = extractImageUrl(document, finalUrl)
        return WebContent(
            url = originalUrl,
            finalUrl = finalUrl,
            title = title,
            bodyText = bodyText,
            imageUrl = imageUrl,
        )
    }

    private fun extractTitle(document: Document): String {
        return listOf(
            document.select("meta[property=og:title]").attr("content"),
            document.select("meta[name=twitter:title]").attr("content"),
            document.title(),
        ).firstOrNull { it.isNotBlank() }.orEmpty()
    }

    private fun extractBodyText(document: Document): String {
        val candidates = mutableListOf<String>()

        ARTICLE_SELECTORS.forEach { selector ->
            document.selectFirst(selector)?.text()?.let { text ->
                normalizeWhitespace(text).takeIf { it.isNotBlank() }?.let { candidates.add(it) }
            }
        }

        candidates.addAll(extractJsonLdText(document))
        candidates.addAll(extractMetaDescriptions(document))

        val cleaned = document.clone()
        cleaned.select("script, style, nav, footer, header, aside, noscript, iframe").remove()
        normalizeWhitespace(cleaned.body()?.text().orEmpty())
            .takeIf { it.isNotBlank() }
            ?.let { candidates.add(it) }

        return candidates
            .distinct()
            .maxByOrNull { it.length }
            .orEmpty()
    }

    private fun extractMetaDescriptions(document: Document): List<String> {
        return listOf(
            document.select("meta[property=og:description]").attr("content"),
            document.select("meta[name=description]").attr("content"),
            document.select("meta[name=twitter:description]").attr("content"),
            document.select("meta[property=article:description]").attr("content"),
        ).map { normalizeWhitespace(it) }.filter { it.isNotBlank() }
    }

    private fun extractJsonLdText(document: Document): List<String> {
        val results = mutableListOf<String>()
        document.select("script").forEach { script ->
            val type = script.attr("type")
            if (!type.contains("ld+json", ignoreCase = true)) return@forEach
            val raw = script.text().trim().ifBlank { script.data().trim() }
            if (raw.isBlank()) return@forEach
            runCatching {
                collectJsonLdText(JSONObject(raw), results)
            }.onFailure {
                runCatching {
                    val array = JSONArray(raw)
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        collectJsonLdText(item, results)
                    }
                }
            }
            JSON_FIELD_REGEX.findAll(raw).forEach { match ->
                normalizeWhitespace(match.groupValues[2])
                    .takeIf { it.length >= MIN_SNIPPET_LENGTH }
                    ?.let { results.add(it) }
            }
        }
        return results
    }

    private fun collectJsonLdText(json: JSONObject, results: MutableList<String>) {
        listOf("articleBody", "description", "text", "abstract").forEach { key ->
            json.optString(key).takeIf { it.isNotBlank() }?.let {
                normalizeWhitespace(it).takeIf { normalized -> normalized.isNotBlank() }?.let(results::add)
            }
        }
        json.optJSONObject("@graph")?.let { collectJsonLdText(it, results) }
        json.optJSONArray("@graph")?.let { graph ->
            for (index in 0 until graph.length()) {
                graph.optJSONObject(index)?.let { collectJsonLdText(it, results) }
            }
        }
    }

    private fun extractImageUrl(document: Document, baseUrl: String): String {
        val ogImage = document.select("meta[property=og:image]").attr("content")
        if (ogImage.isNotBlank()) return resolveUrl(baseUrl, ogImage)
        val twitterImage = document.select("meta[name=twitter:image]").attr("content")
        if (twitterImage.isNotBlank()) return resolveUrl(baseUrl, twitterImage)
        return ""
    }

    private fun resolveUrl(baseUrl: String, path: String): String {
        return try {
            java.net.URL(java.net.URL(baseUrl), path).toString()
        } catch (_: Exception) {
            path
        }
    }

    private fun normalizeWhitespace(text: String): String {
        return text.replace(Regex("\\s+"), " ").trim()
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private const val MAX_REDIRECTS = 5

        private val ARTICLE_SELECTORS = listOf(
            "#dic_area",
            "#newsct_article",
            ".newsct_article",
            "#articeBody",
            "#articleBodyContents",
            "article",
            "main",
            "[role=main]",
            ".article-body",
            ".article_body",
            ".post-content",
            ".entry-content",
            "#content",
            ".content",
        )

        private val JSON_FIELD_REGEX = Regex(""""(articleBody|description|text)"\s*:\s*"((?:\\.|[^"\\])*)"""")
        private const val MIN_SNIPPET_LENGTH = 10
    }
}
