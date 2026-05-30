package com.yoon.js.appsearch.data.web

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Singleton
class HtmlContentExtractor @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
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
            val finalUrl = response.request.url.toString()
            return parseHtml(html, url, finalUrl)
        }
    }

    fun parseHtml(html: String, originalUrl: String, finalUrl: String = originalUrl): WebContent {
        val document = Jsoup.parse(html, finalUrl)
        val title = document.title().ifBlank {
            document.select("meta[property=og:title]").attr("content")
        }
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

    private fun extractBodyText(document: Document): String {
        document.select("script, style, nav, footer, header, aside, noscript").remove()
        val article = document.selectFirst("article")?.text()
        if (!article.isNullOrBlank()) return normalizeWhitespace(article)
        val main = document.selectFirst("main")?.text()
        if (!main.isNullOrBlank()) return normalizeWhitespace(main)
        return normalizeWhitespace(document.body()?.text().orEmpty())
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
    }
}
