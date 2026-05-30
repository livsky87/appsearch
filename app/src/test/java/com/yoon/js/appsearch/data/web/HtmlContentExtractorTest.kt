package com.yoon.js.appsearch.data.web

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlContentExtractorTest {

    private val extractor = HtmlContentExtractor(OkHttpClient.Builder().build())

    @Test
    fun parseHtml_naverArticle_usesDicArea() {
        val html = """
            <html>
            <head>
              <meta property="og:title" content="서소문 붕괴 기사"/>
              <meta property="og:description" content="서울시가 경고를 무시했다는 내용입니다."/>
            </head>
            <body>
              <nav>메뉴</nav>
              <div id="dic_area" class="newsct_article">
                서울 서소문 지하차도 붕괴 사고와 관련해 서울시가 여러 차례 경고를 받았지만 조치하지 않았다는 내용이
                확인됐다. 전문가들은 구조 점검과 유지보수가 필요했다고 지적했다.
              </div>
            </body>
            </html>
        """.trimIndent()

        val content = extractor.parseHtml(html, "https://n.news.naver.com/article/1", "https://n.news.naver.com/article/1")

        assertEquals("서소문 붕괴 기사", content.title)
        assertTrue(content.bodyText.length >= 30)
        assertTrue(content.bodyText.contains("서울 서소문"))
    }

    @Test
    fun parseHtml_fallsBackToOgDescription() {
        val html = """
            <html>
            <head>
              <meta property="og:title" content="짧은 제목"/>
              <meta property="og:description" content="본문이 HTML에 없을 때 og:description을 본문으로 사용합니다. 충분히 긴 설명입니다."/>
            </head>
            <body><div>네이버</div></body>
            </html>
        """.trimIndent()

        val content = extractor.parseHtml(html, "https://example.com")

        assertTrue(content.bodyText.length >= 30)
        assertTrue(content.bodyText.contains("og:description"))
    }

    @Test
    fun parseHtml_jsonLdArticleBody() {
        val html = """
            <html>
            <head>
              <script type="application/ld+json">
                {"@type":"NewsArticle","headline":"JSON-LD 제목","articleBody":"JSON-LD 본문입니다. 기사 내용이 여기에 포함됩니다."}
              </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val content = extractor.parseHtml(html, "https://example.com")

        assertTrue(content.bodyText.contains("JSON-LD 본문"))
    }
}
