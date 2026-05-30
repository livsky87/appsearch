package com.yoon.js.appsearch.data.share

import com.yoon.js.appsearch.data.web.HtmlContentExtractor
import com.yoon.js.appsearch.domain.model.SourceType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ShareIntentParserTest {

    private val parser = ShareIntentParser(
        HtmlContentExtractor(OkHttpClient.Builder().build()),
    )

    @Test
    fun parseSharedText_url_returnsShareWeb() {
        val payload = parser.parseSharedText("https://example.com/article")
        assertNotNull(payload)
        assertEquals(SourceType.SHARE_WEB, payload?.sourceType)
        assertEquals("https://example.com/article", payload?.url)
    }

    @Test
    fun parseSharedText_plainText_returnsShareText() {
        val payload = parser.parseSharedText("메모 내용입니다.")
        assertNotNull(payload)
        assertEquals(SourceType.SHARE_TEXT, payload?.sourceType)
    }
}
