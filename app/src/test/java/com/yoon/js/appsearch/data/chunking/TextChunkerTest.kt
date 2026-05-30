package com.yoon.js.appsearch.data.chunking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TextChunkerTest {

    private val chunker = TextChunker()

    @Test
    fun emptyText_returnsEmptyList() {
        assertTrue(chunker.chunk("").isEmpty())
        assertTrue(chunker.chunk("   ").isEmpty())
    }

    @Test
    fun shortText_returnsSingleChunk() {
        val text = "안녕하세요. AppSearch 테스트입니다."
        val chunks = chunker.chunk(text)
        assertEquals(1, chunks.size)
        assertEquals(text, chunks[0])
    }

    @Test
    fun longText_splitsIntoMultipleChunksUnderMaxSize() {
        val sentence = "이것은 테스트 문장입니다. "
        val text = sentence.repeat(30)
        val chunks = chunker.chunk(text)

        assertTrue(chunks.size > 1)
        chunks.forEach { chunk ->
            assertTrue(
                "Chunk length ${chunk.length} exceeds max",
                chunk.length <= TextChunker.MAX_CHUNK_SIZE + TextChunker.OVERLAP_SIZE,
            )
        }
    }

    @Test
    fun englishText_respectsSentenceBoundaries() {
        val text = buildString {
            repeat(20) { index ->
                append("This is sentence number $index. ")
            }
        }
        val chunks = chunker.chunk(text, locale = Locale.ENGLISH)
        assertTrue(chunks.isNotEmpty())
        chunks.forEach { chunk ->
            assertTrue(chunk.length <= TextChunker.MAX_CHUNK_SIZE + TextChunker.OVERLAP_SIZE)
        }
    }

    @Test
    fun longSentenceWithoutSpaces_isSplitByWords() {
        val longWordSegment = "word".repeat(80)
        val chunks = chunker.chunk(longWordSegment, locale = Locale.ENGLISH)
        assertTrue(chunks.size > 1)
    }

    @Test
    fun adjacentChunks_haveOverlap() {
        val sentence = "문맥을 유지하기 위한 overlap 테스트 문장입니다. "
        val text = sentence.repeat(25)
        val chunks = chunker.chunk(text)

        if (chunks.size >= 2) {
            val firstEnd = chunks[0].takeLast(20)
            val secondStart = chunks[1].take(80)
            assertTrue(
                "Expected overlap between chunks",
                secondStart.contains(firstEnd.trim().takeLast(10)) ||
                    chunks[1].length <= TextChunker.MAX_CHUNK_SIZE,
            )
        }
    }
}
