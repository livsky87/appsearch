package com.yoon.js.appsearch.data.repository

import android.content.Context
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.GetByDocumentIdRequest
import androidx.appsearch.app.PutDocumentsRequest
import androidx.appsearch.app.SearchResult
import androidx.appsearch.app.SearchSpec
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.platformstorage.PlatformStorage
import com.google.common.util.concurrent.ListenableFuture
import com.yoon.js.appsearch.data.chunking.TextChunker
import com.yoon.js.appsearch.data.model.SourceDocument
import com.yoon.js.appsearch.data.model.TextChunkDocument
import com.yoon.js.appsearch.domain.model.ChunkSearchResult
import com.yoon.js.appsearch.domain.model.IndexRequest
import com.yoon.js.appsearch.domain.model.MatchHighlight
import com.yoon.js.appsearch.domain.model.SourceRecord
import com.yoon.js.appsearch.domain.model.SourceType
import com.yoon.js.appsearch.domain.model.TextRange
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class AppSearchRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chunker: TextChunker,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val initMutex = Mutex()
    private var initialized = false

    private val sessionFuture: ListenableFuture<AppSearchSession> by lazy {
        PlatformStorage.createSearchSessionAsync(
            PlatformStorage.SearchContext.Builder(context, DATABASE_NAME).build(),
        )
    }

    suspend fun indexContent(request: IndexRequest): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            ensureInitialized()
            val trimmed = request.text.trim()
            if (trimmed.isEmpty()) return@runCatching 0L

            val sourceId = System.currentTimeMillis()
            val chunks = chunker.chunk(trimmed)
            if (chunks.isEmpty()) return@runCatching 0L

            val timestamp = System.currentTimeMillis()
            val sourceDocument = SourceDocument.create(
                sourceId = sourceId,
                sourceType = request.sourceType,
                title = request.title,
                url = request.url,
                imageUrl = request.imageUrl,
                previewText = trimmed,
                chunkCount = chunks.size,
                creationTimestampMillis = timestamp,
            )
            val chunkDocuments = chunks.mapIndexed { index, chunk ->
                TextChunkDocument.create(
                    sourceId = sourceId,
                    chunkIndex = index,
                    sourceType = request.sourceType,
                    content = chunk,
                    creationTimestampMillis = timestamp,
                )
            }

            val session = sessionFuture.await()
            val putRequest = PutDocumentsRequest.Builder()
                .addDocuments(sourceDocument)
                .addDocuments(chunkDocuments)
                .build()
            session.putAsync(putRequest).await()
            session.requestFlushAsync().await()
            sourceId
        }
    }

    suspend fun indexText(text: String): Result<Int> = withContext(ioDispatcher) {
        indexContent(
            IndexRequest(
                text = text,
                sourceType = SourceType.MANUAL,
            ),
        ).map { sourceId ->
            if (sourceId == 0L) 0 else {
                getSource(sourceId)?.chunkCount ?: 0
            }
        }
    }

    suspend fun listSources(): Result<List<SourceRecord>> = withContext(ioDispatcher) {
        runCatching {
            ensureInitialized()
            val session = sessionFuture.await()
            val searchSpec = SearchSpec.Builder()
                .addFilterSchemas(SourceDocument.SCHEMA_TYPE)
                .setRankingStrategy(SearchSpec.RANKING_STRATEGY_CREATION_TIMESTAMP)
                .build()

            val searchResults = session.search("", searchSpec)
            val records = mutableListOf<SourceRecord>()
            try {
                var page = searchResults.getNextPageAsync().await()
                while (page.isNotEmpty()) {
                    page.forEach { searchResult ->
                        records.add(mapSourceRecord(searchResult))
                    }
                    page = searchResults.getNextPageAsync().await()
                }
            } finally {
                searchResults.close()
            }
            records.sortedByDescending { it.creationTimestampMillis }
        }
    }

    suspend fun getSource(sourceId: Long): SourceRecord? = withContext(ioDispatcher) {
        runCatching {
            ensureInitialized()
            val session = sessionFuture.await()
            val request = GetByDocumentIdRequest.Builder(SourceDocument.NAMESPACE)
                .addIds(sourceId.toString())
                .build()
            val result = session.getByDocumentIdAsync(request).await()
            val document = result.getSuccesses()[sourceId.toString()] ?: return@runCatching null
            mapSourceDocument(document.toDocumentClass(SourceDocument::class.java))
        }.getOrNull()
    }

    suspend fun search(query: String): Result<List<ChunkSearchResult>> = withContext(ioDispatcher) {
        runCatching {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@runCatching emptyList()

            ensureInitialized()
            val session = sessionFuture.await()
            val searchSpec = SearchSpec.Builder()
                .addFilterSchemas(TextChunkDocument.SCHEMA_TYPE)
                .setRankingStrategy(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE)
                .setSnippetCount(SNIPPET_COUNT)
                .setSnippetCountPerProperty(SNIPPET_COUNT_PER_PROPERTY)
                .setMaxSnippetSize(MAX_SNIPPET_SIZE)
                .build()

            val searchResults = session.search(trimmed, searchSpec)
            val results = mutableListOf<ChunkSearchResult>()
            try {
                var page = searchResults.getNextPageAsync().await()
                while (page.isNotEmpty()) {
                    page.forEach { searchResult ->
                        results.add(mapSearchResult(searchResult))
                    }
                    page = searchResults.getNextPageAsync().await()
                }
            } finally {
                searchResults.close()
            }
            results
        }
    }

    private suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            val session = sessionFuture.await()
            val setSchemaRequest = SetSchemaRequest.Builder()
                .addDocumentClasses(SourceDocument::class.java, TextChunkDocument::class.java)
                .setForceOverride(true)
                .build()
            session.setSchemaAsync(setSchemaRequest).await()
            initialized = true
        }
    }

    private fun mapSourceRecord(searchResult: SearchResult): SourceRecord {
        val document = searchResult.genericDocument.toDocumentClass(SourceDocument::class.java)
        return mapSourceDocument(document)
    }

    private fun mapSourceDocument(document: SourceDocument): SourceRecord {
        return SourceRecord(
            sourceId = document.id.toLong(),
            sourceType = SourceType.fromCode(document.sourceType),
            title = document.title,
            url = document.url,
            imageUrl = document.imageUrl,
            previewText = document.previewText,
            creationTimestampMillis = document.creationTimestampMillis,
            chunkCount = document.chunkCount.toInt(),
        )
    }

    private suspend fun mapSearchResult(searchResult: SearchResult): ChunkSearchResult {
        val document = searchResult.genericDocument.toDocumentClass(TextChunkDocument::class.java)
        val source = getSource(document.sourceId)
        return ChunkSearchResult(
            id = document.id,
            sourceId = document.sourceId,
            chunkIndex = document.chunkIndex.toInt(),
            content = document.content,
            relevanceScore = searchResult.rankingSignal,
            sourceType = SourceType.fromCode(document.sourceType),
            sourceTitle = source?.title.orEmpty(),
            sourceUrl = source?.url.orEmpty(),
            matches = mapMatchInfos(searchResult),
        )
    }

    private fun mapMatchInfos(searchResult: SearchResult): List<MatchHighlight> {
        return searchResult.matchInfos.mapNotNull { matchInfo ->
            val snippet = matchInfo.snippet?.toString()
                ?: matchInfo.textMatch?.snippet?.toString()
                ?: matchInfo.fullText?.toString()
                ?: return@mapNotNull null

            val range = matchInfo.snippetRange
                ?: matchInfo.exactMatchRange
                ?: matchInfo.submatchRange
                ?: matchInfo.textMatch?.snippetRange
                ?: matchInfo.textMatch?.exactMatchRange
                ?: return@mapNotNull null

            MatchHighlight(
                propertyPath = matchInfo.propertyPath,
                snippet = snippet,
                matchedTerm = matchInfo.exactMatch?.toString()
                    ?: matchInfo.submatch?.toString()
                    ?: matchInfo.textMatch?.exactMatch?.toString()
                    ?: "",
                ranges = listOf(
                    TextRange(
                        start = range.start,
                        end = range.end,
                    ),
                ),
            )
        }
    }

    companion object {
        private const val DATABASE_NAME = "appsearch_db"
        private const val SNIPPET_COUNT = 50
        private const val SNIPPET_COUNT_PER_PROPERTY = 5
        private const val MAX_SNIPPET_SIZE = 120
    }
}
