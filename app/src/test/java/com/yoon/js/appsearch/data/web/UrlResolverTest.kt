package com.yoon.js.appsearch.data.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlResolverTest {

    @Test
    fun unwrapBridgeUrl_extractsEmbeddedArticleUrl() {
        val bridge =
            "https://link.naver.com/bridge?url=https%3A%2F%2Fn.news.naver.com%2Farticle%2F056%2F0012191086%3Fcds%3Dnews_edit"
        val resolved = UrlResolver.unwrapBridgeUrl(bridge)
        assertEquals(
            "https://n.news.naver.com/article/056/0012191086?cds=news_edit",
            resolved,
        )
    }

    @Test
    fun selectBestFetchUrl_prefersOgUrlOverShortLink() {
        val best = UrlResolver.selectBestFetchUrl(
            listOf(
                "https://naver.me/5B00ATEO",
                "https://n.news.naver.com/article/056/0012191086?cds=news_edit",
                "https://link.naver.com/bridge?url=https%3A%2F%2Fn.news.naver.com%2Farticle%2F056%2F0012191086",
            ),
        )
        assertEquals("https://n.news.naver.com/article/056/0012191086?cds=news_edit", best)
    }

    @Test
    fun isIntermediateUrl_detectsShortAndBridgeLinks() {
        assertTrue(UrlResolver.isIntermediateUrl("https://naver.me/abc"))
        assertTrue(
            UrlResolver.isIntermediateUrl(
                "https://link.naver.com/bridge?url=https%3A%2F%2Fn.news.naver.com%2Farticle%2F1",
            ),
        )
        assertTrue(!UrlResolver.isIntermediateUrl("https://n.news.naver.com/article/056/0012191086"))
    }

    @Test
    fun isDirectArticleUrl_acceptsUnwrappedNaverNews() {
        assertTrue(UrlResolver.isDirectArticleUrl("https://n.news.naver.com/article/056/0012191086"))
        assertTrue(!UrlResolver.isDirectArticleUrl("https://naver.me/abc"))
    }
}
