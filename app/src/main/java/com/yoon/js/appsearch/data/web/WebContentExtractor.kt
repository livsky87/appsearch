package com.yoon.js.appsearch.data.web

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
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
    suspend fun extract(url: String): WebContent {
        return try {
            withTimeout(TIMEOUT_MS) {
                extractWithWebView(url)
            }
        } catch (_: Exception) {
            htmlContentExtractor.extract(url)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun extractWithWebView(url: String): WebContent = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val webView = WebView(context.applicationContext)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.loadsImagesAutomatically = false

            var finished = false

            fun cleanup() {
                webView.stopLoading()
                webView.destroy()
            }

            continuation.invokeOnCancellation { cleanup() }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, loadedUrl: String) {
                    if (finished) return
                    view.evaluateJavascript(EXTRACT_SCRIPT) { result ->
                        if (finished) return@evaluateJavascript
                        finished = true
                        try {
                            val json = JSONObject(decodeJsJson(result))
                            val content = WebContent(
                                url = url,
                                finalUrl = loadedUrl,
                                title = json.optString("title"),
                                bodyText = json.optString("bodyText"),
                                imageUrl = json.optString("imageUrl"),
                            )
                            cleanup()
                            if (content.bodyText.isBlank()) {
                                continuation.resumeWithException(IllegalStateException("Empty body"))
                            } else {
                                continuation.resume(content)
                            }
                        } catch (e: Exception) {
                            cleanup()
                            continuation.resumeWithException(e)
                        }
                    }
                }
            }

            webView.loadUrl(url)
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
        private const val TIMEOUT_MS = 15_000L
        private const val EXTRACT_SCRIPT = """
            (function() {
                function meta(selector) {
                    var el = document.querySelector(selector);
                    return el ? el.content : '';
                }
                var imageUrl = meta('meta[property="og:image"]') || meta('meta[name="twitter:image"]') || '';
                var bodyText = document.body ? document.body.innerText : '';
                bodyText = bodyText.replace(/\s+/g, ' ').trim();
                return JSON.stringify({
                    title: document.title || '',
                    bodyText: bodyText,
                    imageUrl: imageUrl
                });
            })();
        """
    }
}
