package com.yoon.js.appsearch.data.share

import android.content.Intent
import com.yoon.js.appsearch.data.web.UrlResolver
import com.yoon.js.appsearch.domain.model.SourceType
import javax.inject.Inject
import javax.inject.Singleton

data class SharePayload(
    val sourceType: SourceType,
    val text: String,
    val url: String = "",
    val fetchUrls: List<String> = emptyList(),
    val html: String = "",
    val titleHint: String = "",
    val descriptionHint: String = "",
    val imageUrlHint: String = "",
)

@Singleton
class ShareIntentParser @Inject constructor(
    private val htmlContentExtractor: com.yoon.js.appsearch.data.web.HtmlContentExtractor,
) {
    fun parse(intent: Intent): SharePayload? {
        if (intent.action != Intent.ACTION_SEND) {
            ShareFlowLogger.d("ShareParser", "skip action=${intent.action}")
            return null
        }
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val sharedHtml = intent.getStringExtra(Intent.EXTRA_HTML_TEXT)?.trim().orEmpty()
        val ogUrl = intent.getStringExtra(EXTRA_OG_URL)?.trim().orEmpty()
        val originalUrl = intent.getStringExtra(EXTRA_ORIGINAL_URL)?.trim().orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE)?.trim()
            ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        val ogDescription = intent.getStringExtra(EXTRA_OG_DESCRIPTION)?.trim().orEmpty()
        val ogImage = intent.getStringExtra(EXTRA_OG_IMAGE)?.trim().orEmpty()

        ShareFlowLogger.d(
            "ShareParser",
            "EXTRA_TEXT=${ShareFlowLogger.preview(sharedText, 100)} htmlLen=${sharedHtml.length} " +
                "ogUrl=${ShareFlowLogger.preview(ogUrl, 80)} originalUrl=${ShareFlowLogger.preview(originalUrl, 80)}",
        )

        return parseSharedContent(
            sharedText = sharedText,
            sharedHtml = sharedHtml,
            ogUrl = ogUrl,
            originalUrl = originalUrl,
            titleHint = title,
            descriptionHint = ogDescription,
            imageUrlHint = ogImage,
        )
    }

    fun parseSharedText(sharedText: String, sharedHtml: String = ""): SharePayload? {
        return parseSharedContent(
            sharedText = sharedText,
            sharedHtml = sharedHtml,
        )
    }

    private fun parseSharedContent(
        sharedText: String,
        sharedHtml: String = "",
        ogUrl: String = "",
        originalUrl: String = "",
        titleHint: String = "",
        descriptionHint: String = "",
        imageUrlHint: String = "",
    ): SharePayload? {
        if (sharedText.isBlank() && sharedHtml.isBlank() && ogUrl.isBlank() && originalUrl.isBlank()) {
            ShareFlowLogger.w("ShareParser", "share payload empty")
            return null
        }

        val textUrl = extractUrl(sharedText)
        val fetchUrls = UrlResolver.orderedFetchUrls(
            listOfNotNull(
                ogUrl.takeIf { it.isNotBlank() },
                originalUrl.takeIf { it.isNotBlank() },
                textUrl,
            ),
        )
        val primaryUrl = UrlResolver.selectBestFetchUrl(fetchUrls).ifBlank { textUrl.orEmpty() }

        if (primaryUrl.isNotBlank() || textUrl != null) {
            ShareFlowLogger.d(
                "ShareParser",
                "web share primary=${ShareFlowLogger.preview(primaryUrl, 100)} fetchUrls=${fetchUrls.size}",
            )
            val htmlMeta = if (sharedHtml.isNotBlank() && primaryUrl.isNotBlank()) {
                htmlContentExtractor.parseHtml(sharedHtml, primaryUrl)
            } else {
                null
            }
            return SharePayload(
                sourceType = SourceType.SHARE_WEB,
                text = sharedText.ifBlank { primaryUrl },
                url = primaryUrl,
                fetchUrls = fetchUrls,
                html = sharedHtml,
                titleHint = titleHint.ifBlank { htmlMeta?.title.orEmpty() },
                descriptionHint = descriptionHint,
                imageUrlHint = imageUrlHint.ifBlank { htmlMeta?.imageUrl.orEmpty() },
            )
        }

        ShareFlowLogger.d("ShareParser", "plain text share len=${sharedText.length}")
        return SharePayload(
            sourceType = SourceType.SHARE_TEXT,
            text = sharedText.ifBlank { sharedHtml },
        )
    }

    private fun extractUrl(text: String): String? {
        val match = URL_REGEX.find(text.trim()) ?: return null
        return match.value
    }

    companion object {
        private val URL_REGEX = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)

        const val EXTRA_OG_URL = "ogUrl"
        const val EXTRA_ORIGINAL_URL = "originalUrl"
        const val EXTRA_TITLE = "title"
        const val EXTRA_OG_DESCRIPTION = "ogDescription"
        const val EXTRA_OG_IMAGE = "ogImage"
    }
}
