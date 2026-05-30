package com.yoon.js.appsearch.data.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.yoon.js.appsearch.data.share.ShareFlowLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class WebContentExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val htmlContentExtractor: HtmlContentExtractor,
) {
    suspend fun extract(request: WebExtractRequest): WebContent {
        val fetchUrls = UrlResolver.orderedFetchUrls(
            request.fetchUrls + listOfNotNull(request.url.takeIf { it.isNotBlank() }),
        )
        ShareFlowLogger.d(
            "WebExtract",
            "start fetchUrls=${fetchUrls.joinToString { ShareFlowLogger.preview(it, 60) }} " +
                "htmlLen=${request.sharedHtml.length} titleHint=${ShareFlowLogger.preview(request.titleHint, 60)}",
        )

        val candidates = mutableListOf<Pair<String, WebContent>>()
        var resolvedUrl = fetchUrls.firstOrNull().orEmpty().ifBlank { request.url }

        for (fetchUrl in fetchUrls) {
            runCatching {
                withContext(Dispatchers.IO) {
                    htmlContentExtractor.extract(fetchUrl)
                }
            }.onSuccess { content ->
                val normalized = content.copy(
                    finalUrl = UrlResolver.normalize(content.finalUrl.ifBlank { fetchUrl }),
                    url = request.url.ifBlank { fetchUrl },
                )
                candidates.add("okhttp:$fetchUrl" to normalized)
                if (UrlResolver.isDirectArticleUrl(normalized.finalUrl)) {
                    resolvedUrl = normalized.finalUrl
                } else if (resolvedUrl.isBlank() || UrlResolver.isIntermediateUrl(resolvedUrl)) {
                    resolvedUrl = UrlResolver.normalize(normalized.finalUrl)
                }
                ShareFlowLogger.d(
                    "WebExtract",
                    "okhttp[$fetchUrl] title=${ShareFlowLogger.preview(normalized.title, 60)} " +
                        "bodyLen=${normalized.bodyText.length} finalUrl=${ShareFlowLogger.preview(normalized.finalUrl, 80)}",
                )
            }.onFailure { error ->
                ShareFlowLogger.w("WebExtract", "okhttp[$fetchUrl] failed: ${error.message}", error)
            }
        }

        if (candidates.none { it.second.bodyText.length >= PREFERRED_BODY_LENGTH } && fetchUrls.isNotEmpty()) {
            runCatching {
                withContext(Dispatchers.IO) {
                    htmlContentExtractor.resolveFinalUrl(fetchUrls.first())
                }
            }.onSuccess { url ->
                if (url.isNotBlank() && fetchUrls.none { UrlResolver.normalize(it) == url }) {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            htmlContentExtractor.extract(url)
                        }
                    }.onSuccess { content ->
                        val normalized = content.copy(
                            finalUrl = UrlResolver.normalize(content.finalUrl.ifBlank { url }),
                            url = request.url.ifBlank { url },
                        )
                        candidates.add("okhttp:resolved" to normalized)
                        resolvedUrl = normalized.finalUrl
                        ShareFlowLogger.d(
                            "WebExtract",
                            "okhttp[resolved] bodyLen=${normalized.bodyText.length} " +
                                "finalUrl=${ShareFlowLogger.preview(resolvedUrl, 80)}",
                        )
                    }
                } else if (UrlResolver.isDirectArticleUrl(url)) {
                    resolvedUrl = url
                }
            }
        }

        if (request.sharedHtml.isNotBlank()) {
            runCatching {
                htmlContentExtractor.parseHtml(
                    request.sharedHtml,
                    request.url.ifBlank { resolvedUrl },
                    resolvedUrl,
                )
            }.onSuccess {
                candidates.add("sharedHtml" to it)
                ShareFlowLogger.d(
                    "WebExtract",
                    "sharedHtml OK title=${ShareFlowLogger.preview(it.title, 60)} bodyLen=${it.bodyText.length}",
                )
            }.onFailure { error ->
                ShareFlowLogger.w("WebExtract", "sharedHtml failed: ${error.message}", error)
            }
        }

        val webViewUrl = fetchUrls.firstOrNull { UrlResolver.isDirectArticleUrl(it) }
            ?: UrlResolver.normalize(resolvedUrl).takeIf { it.isNotBlank() && !UrlResolver.isIntermediateUrl(it) }
            ?: fetchUrls.firstOrNull().orEmpty()

        if (webViewUrl.isNotBlank()) {
            runCatching {
                withTimeout(WEBVIEW_TIMEOUT_MS) {
                    extractWithWebView(loadUrl = webViewUrl, originalUrl = request.url.ifBlank { webViewUrl })
                }
            }.onSuccess {
                candidates.add("webview" to it)
                ShareFlowLogger.d(
                    "WebExtract",
                    "webview OK title=${ShareFlowLogger.preview(it.title, 60)} bodyLen=${it.bodyText.length} " +
                        "loaded=${ShareFlowLogger.preview(it.finalUrl, 100)}",
                )
            }.onFailure { error ->
                ShareFlowLogger.w("WebExtract", "webview failed: ${error.message}", error)
            }
        }

        if (request.descriptionHint.isNotBlank() && request.descriptionHint.length >= MIN_BODY_LENGTH) {
            candidates.add(
                "descriptionHint" to WebContent(
                    url = request.url,
                    finalUrl = resolvedUrl.ifBlank { request.url },
                    title = request.titleHint,
                    bodyText = request.descriptionHint,
                    imageUrl = request.imageUrlHint,
                ),
            )
        }

        candidates.forEach { (source, content) ->
            val valid = WebContentValidator.isValid(content.title, content.bodyText)
            ShareFlowLogger.d(
                "WebExtract",
                "candidate[$source] valid=$valid title=${ShareFlowLogger.preview(content.title, 60)} " +
                    "bodyLen=${content.bodyText.length} errorBody=${WebContentValidator.isErrorBody(content.bodyText)}",
            )
        }

        val best = selectBestContent(candidates.map { it.second })
        if (best == null) {
            ShareFlowLogger.e(
                "WebExtract",
                "no valid candidate (total=${candidates.size}) url=${ShareFlowLogger.preview(request.url, 100)}",
            )
            throw WebContentExtractionException("페이지 내용을 읽을 수 없습니다")
        }

        ShareFlowLogger.d(
            "WebExtract",
            "selected bodyLen=${best.bodyText.length} title=${ShareFlowLogger.preview(best.title, 60)}",
        )

        return best.copy(
            title = WebContentValidator.sanitizeTitle(
                title = best.title.ifBlank { request.titleHint },
                url = best.finalUrl.ifBlank { request.url },
                previewText = best.bodyText,
            ),
            imageUrl = best.imageUrl.ifBlank { request.imageUrlHint },
        )
    }

    private fun selectBestContent(candidates: List<WebContent>): WebContent? {
        val validCandidates = candidates.filter { WebContentValidator.isValid(it.title, it.bodyText) }
        if (validCandidates.isNotEmpty()) {
            return validCandidates.maxByOrNull { it.bodyText.length }
        }

        if (candidates.isEmpty()) return null

        val bestTitle = candidates
            .map { it.title.trim() }
            .filter { it.isNotBlank() && !WebContentValidator.isErrorTitle(it) }
            .maxByOrNull { it.length }
            .orEmpty()

        val bestBody = candidates.maxByOrNull { it.bodyText.length }?.bodyText.orEmpty()
        val bestFinalUrl = candidates.maxByOrNull { it.finalUrl.length }?.finalUrl.orEmpty()
        val bestImage = candidates.firstOrNull { it.imageUrl.isNotBlank() }?.imageUrl.orEmpty()
        val originalUrl = candidates.firstOrNull { it.url.isNotBlank() }?.url.orEmpty()

        val merged = WebContent(
            url = originalUrl,
            finalUrl = bestFinalUrl,
            title = bestTitle,
            bodyText = bestBody,
            imageUrl = bestImage,
        )
        return merged.takeIf { WebContentValidator.isValid(it.title, it.bodyText) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun extractWithWebView(loadUrl: String, originalUrl: String): WebContent =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val webView = WebView(context.applicationContext)
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadsImagesAutomatically = true
                    blockNetworkLoads = false
                    blockNetworkImage = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }

                val mainHandler = Handler(Looper.getMainLooper())
                var finished = false
                var bestContent: WebContent? = null

                fun cleanup() {
                    mainHandler.removeCallbacksAndMessages(null)
                    webView.stopLoading()
                    webView.destroy()
                }

                fun tryComplete(force: Boolean = false) {
                    if (finished) return
                    val content = bestContent
                    if (content == null) {
                        if (force) {
                            finished = true
                            cleanup()
                            continuation.resumeWithException(IllegalStateException("Empty body"))
                        }
                        return
                    }
                    if (content.bodyText.length >= MIN_BODY_LENGTH || force) {
                        finished = true
                        cleanup()
                        continuation.resume(content)
                    }
                }

                fun extractNow(loadedUrl: String) {
                    webView.evaluateJavascript(EXTRACT_SCRIPT) { result ->
                        if (finished) return@evaluateJavascript
                        runCatching {
                            val json = JSONObject(decodeJsJson(result))
                            val bodyText = json.optString("bodyText")
                            if (bodyText.isBlank()) return@evaluateJavascript
                            val candidate = WebContent(
                                url = originalUrl,
                                finalUrl = loadedUrl,
                                title = json.optString("title"),
                                bodyText = bodyText,
                                imageUrl = json.optString("imageUrl"),
                            )
                            if (bestContent == null || candidate.bodyText.length > bestContent!!.bodyText.length) {
                                bestContent = candidate
                            }
                            tryComplete(force = false)
                        }
                    }
                }

                continuation.invokeOnCancellation { cleanup() }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, loadedUrl: String) {
                        if (finished) return
                        mainHandler.removeCallbacksAndMessages(null)
                        mainHandler.postDelayed({ extractNow(loadedUrl) }, PAGE_SETTLE_MS)
                        mainHandler.postDelayed({ extractNow(loadedUrl) }, PAGE_SETTLE_MS * 2)
                        mainHandler.postDelayed({ extractNow(loadedUrl) }, PAGE_SETTLE_MS * 4)
                        mainHandler.postDelayed({ tryComplete(force = true) }, FINAL_TIMEOUT_MS)
                    }
                }

                ShareFlowLogger.d("WebExtract", "webview loadUrl=${ShareFlowLogger.preview(loadUrl, 100)}")
                webView.loadUrl(loadUrl)
            }
        }

    private fun decodeJsJson(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return "{}"
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) return trimmed
        return trimmed
            .removeSurrounding("\"")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\/", "/")
    }

    companion object {
        private const val WEBVIEW_TIMEOUT_MS = 25_000L
        private const val PAGE_SETTLE_MS = 1_000L
        private const val FINAL_TIMEOUT_MS = 6_000L
        private const val MIN_BODY_LENGTH = 30
        private const val PREFERRED_BODY_LENGTH = 200
        private const val EXTRACT_SCRIPT = """
            (function() {
                function meta(selector) {
                    var el = document.querySelector(selector);
                    return el ? el.content : '';
                }
                var selectors = [
                    '#dic_area',
                    '#newsct_article',
                    '.newsct_article',
                    '#articeBody',
                    'article',
                    'main',
                    '[role=main]'
                ];
                var bodyText = '';
                for (var i = 0; i < selectors.length; i++) {
                    var el = document.querySelector(selectors[i]);
                    if (el) {
                        var text = (el.innerText || '').replace(/\s+/g, ' ').trim();
                        if (text.length > bodyText.length) bodyText = text;
                    }
                }
                if (!bodyText && document.body) {
                    bodyText = (document.body.innerText || '').replace(/\s+/g, ' ').trim();
                }
                var description = meta('meta[property="og:description"]')
                    || meta('meta[name=description]')
                    || meta('meta[name=twitter:description]')
                    || '';
                if (bodyText.length < 100 && description) {
                    bodyText = description + (bodyText ? (' ' + bodyText) : '');
                }
                var imageUrl = meta('meta[property="og:image"]') || meta('meta[name=twitter:image]') || '';
                var title = document.title
                    || meta('meta[property="og:title"]')
                    || meta('meta[name=twitter:title]')
                    || '';
                return JSON.stringify({
                    title: title,
                    bodyText: bodyText,
                    imageUrl: imageUrl
                });
            })();
        """
    }
}
