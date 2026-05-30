package com.yoon.js.appsearch.data.share

import android.content.Intent
import com.yoon.js.appsearch.domain.model.SourceType
import javax.inject.Inject
import javax.inject.Singleton

data class SharePayload(
    val sourceType: SourceType,
    val text: String,
    val url: String = "",
    val html: String = "",
    val titleHint: String = "",
    val imageUrlHint: String = "",
)

@Singleton
class ShareIntentParser @Inject constructor(
    private val htmlContentExtractor: com.yoon.js.appsearch.data.web.HtmlContentExtractor,
) {
    fun parse(intent: Intent): SharePayload? {
        if (intent.action != Intent.ACTION_SEND) return null
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val sharedHtml = intent.getStringExtra(Intent.EXTRA_HTML_TEXT)?.trim().orEmpty()
        return parseSharedText(sharedText, sharedHtml)
    }

    fun parseSharedText(sharedText: String, sharedHtml: String = ""): SharePayload? {
        if (sharedText.isBlank() && sharedHtml.isBlank()) return null

        val url = extractUrl(sharedText)
        return if (url != null) {
            val htmlMeta = if (sharedHtml.isNotBlank()) {
                htmlContentExtractor.parseHtml(sharedHtml, url)
            } else {
                null
            }
            SharePayload(
                sourceType = SourceType.SHARE_WEB,
                text = url,
                url = url,
                html = sharedHtml,
                titleHint = htmlMeta?.title.orEmpty(),
                imageUrlHint = htmlMeta?.imageUrl.orEmpty(),
            )
        } else {
            SharePayload(
                sourceType = SourceType.SHARE_TEXT,
                text = sharedText.ifBlank { sharedHtml },
            )
        }
    }

    private fun extractUrl(text: String): String? {
        val match = URL_REGEX.find(text.trim()) ?: return null
        return match.value
    }

    companion object {
        private val URL_REGEX = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
    }
}
