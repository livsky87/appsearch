package com.yoon.js.appsearch.data.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebContentValidatorTest {

    @Test
    fun isErrorTitle_detectsWebViewErrorPage() {
        assertTrue(WebContentValidator.isErrorTitle("웹페이지를 사용할 수 없음"))
        assertTrue(WebContentValidator.isErrorTitle("This site can't be reached"))
        assertFalse(WebContentValidator.isErrorTitle("네이버 뉴스"))
    }

    @Test
    fun isValid_acceptsLongBodyEvenWithErrorTitle() {
        assertTrue(
            WebContentValidator.isValid(
                title = "웹페이지를 사용할 수 없음",
                bodyText = "본문 내용이 충분히 길어서 검색 인덱싱에 사용할 수 있습니다. " +
                    "오류 페이지 제목이어도 본문이 정상이면 저장합니다.",
            ),
        )
    }

    @Test
    fun isValid_rejectsShortErrorPageBody() {
        assertFalse(
            WebContentValidator.isValid(
                title = "웹페이지를 사용할 수 없음",
                bodyText = "웹페이지를 사용할 수 없음. 인터넷 연결을 확인하세요.",
            ),
        )
    }

    @Test
    fun isValid_acceptsNormalArticle() {
        assertTrue(
            WebContentValidator.isValid(
                title = "기사 제목",
                bodyText = "본문 내용이 충분히 길어서 검색 인덱싱에 사용할 수 있습니다.",
            ),
        )
    }

    @Test
    fun sanitizeTitle_usesHostWhenTitleIsErrorPage() {
        assertEquals(
            "example.com",
            WebContentValidator.sanitizeTitle(
                title = "웹페이지를 사용할 수 없음",
                url = "https://www.example.com/article",
                previewText = "preview",
            ),
        )
    }

    @Test
    fun sanitizeTitle_keepsValidTitle() {
        assertEquals(
            "기사 제목",
            WebContentValidator.sanitizeTitle(
                title = "기사 제목",
                url = "https://example.com",
                previewText = "preview",
            ),
        )
    }
}
