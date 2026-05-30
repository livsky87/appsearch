package com.yoon.js.appsearch.data.chunking

import java.text.BreakIterator
import java.util.Locale
import javax.inject.Inject

class TextChunker @Inject constructor() {

    fun chunk(
        text: String,
        maxChunkSize: Int = MAX_CHUNK_SIZE,
        overlapSize: Int = OVERLAP_SIZE,
        locale: Locale = Locale.getDefault(),
    ): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val sentences = splitIntoSentences(trimmed, locale)
        val units = buildList {
            for (sentence in sentences) {
                if (sentence.length <= maxChunkSize) {
                    add(sentence)
                } else {
                    addAll(splitIntoWordSegments(sentence, maxChunkSize, locale))
                }
            }
        }

        return packWithOverlap(units, maxChunkSize, overlapSize)
    }

    private fun splitIntoSentences(text: String, locale: Locale): List<String> {
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(text)
        val sentences = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            text.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let { sentences.add(it) }
            start = end
            end = iterator.next()
        }
        return sentences
    }

    private fun splitIntoWordSegments(
        text: String,
        maxChunkSize: Int,
        locale: Locale,
    ): List<String> {
        val iterator = BreakIterator.getWordInstance(locale)
        iterator.setText(text)
        val segments = mutableListOf<String>()
        var current = StringBuilder()

        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val word = text.substring(start, end)
            if (word.isBlank()) {
                start = end
                end = iterator.next()
                continue
            }

            val separator = if (current.isEmpty()) "" else " "
            val candidate = current.toString() + separator + word
            if (candidate.length <= maxChunkSize) {
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            } else {
                if (current.isNotEmpty()) {
                    segments.add(current.toString())
                    current = StringBuilder()
                }
                if (word.length <= maxChunkSize) {
                    current.append(word)
                } else {
                    word.chunked(maxChunkSize).forEach { segments.add(it) }
                }
            }
            start = end
            end = iterator.next()
        }

        if (current.isNotEmpty()) {
            segments.add(current.toString())
        }
        return segments
    }

    private fun packWithOverlap(
        units: List<String>,
        maxChunkSize: Int,
        overlapSize: Int,
    ): List<String> {
        if (units.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        var current = StringBuilder()

        for (unit in units) {
            val separator = if (current.isEmpty()) "" else " "
            val candidate = current.toString() + separator + unit

            if (candidate.length <= maxChunkSize) {
                if (current.isNotEmpty()) current.append(' ')
                current.append(unit)
            } else {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString())
                    val overlapPrefix = createOverlapPrefix(chunks.last(), overlapSize)
                    current = StringBuilder(overlapPrefix)
                    val retrySeparator = if (current.isEmpty()) "" else " "
                    val retryCandidate = current.toString() + retrySeparator + unit
                    if (retryCandidate.length <= maxChunkSize) {
                        if (current.isNotEmpty()) current.append(' ')
                        current.append(unit)
                    } else {
                        if (current.isNotEmpty()) {
                            chunks.add(current.toString())
                            current = StringBuilder()
                        }
                        current.append(unit)
                    }
                } else {
                    current.append(unit)
                }
            }
        }

        if (current.isNotEmpty()) {
            chunks.add(current.toString())
        }

        return chunks.filter { it.isNotBlank() }
    }

    private fun createOverlapPrefix(chunk: String, overlapSize: Int): String {
        if (chunk.length <= overlapSize) return chunk
        val overlap = chunk.takeLast(overlapSize)
        val wordBreak = overlap.indexOf(' ')
        return if (wordBreak in 1 until overlap.lastIndex) {
            overlap.substring(wordBreak + 1)
        } else {
            overlap
        }
    }

    companion object {
        const val MAX_CHUNK_SIZE = 300
        const val OVERLAP_SIZE = 50
    }
}
