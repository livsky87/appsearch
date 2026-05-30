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
import com.yoon.js.appsearch.data.share.ShareFlowLogger
import com.yoon.js.appsearch.data.model.SourceDocument
import com.yoon.js.appsearch.data.model.TextChunkDocument
import com.yoon.js.appsearch.domain.model.ChunkSearchResult
import com.yoon.js.appsearch.domain.model.IndexRequest
import com.yoon.js.appsearch.domain.model.MatchHighlight
import com.yoon.js.appsearch.domain.model.SourceDetail
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
    private val sourceRegistry: SourceRegistry,
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
        ShareFlowLogger.d(
            "Index",
            "start type=${request.sourceType} textLen=${request.text.length} " +
                "title=${ShareFlowLogger.preview(request.title, 60)} url=${ShareFlowLogger.preview(request.url, 80)}",
        )
        runCatching {
            ensureInitialized()
            val trimmed = request.text.trim()
            if (trimmed.isEmpty()) error("저장할 내용이 없습니다")

            val sourceId = System.currentTimeMillis()
            val chunks = chunker.chunk(trimmed)
            ShareFlowLogger.d("Index", "chunked sourceId=$sourceId chunkCount=${chunks.size}")
            if (chunks.isEmpty()) error("저장할 내용이 없습니다")

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
            val putResult = session.putAsync(putRequest).await()
            val failures = putResult.getFailures()
            if (failures.isNotEmpty()) {
                val message = failures.values.joinToString { it.errorMessage ?: "unknown" }
                ShareFlowLogger.e("Index", "putAsync failures: $message")
                error("Indexing failed: $message")
            }
            session.requestFlushAsync().await()

            val sourceRecord = SourceRecord(
                sourceId = sourceId,
                sourceType = request.sourceType,
                title = sourceDocument.title,
                url = request.url,
                imageUrl = request.imageUrl,
                previewText = sourceDocument.previewText,
                creationTimestampMillis = timestamp,
                chunkCount = chunks.size,
            )
            sourceRegistry.save(sourceRecord)
            ShareFlowLogger.d(
                "Index",
                "saved sourceId=$sourceId title=${ShareFlowLogger.preview(sourceRecord.title, 60)} " +
                    "registryCount=${sourceRegistry.listAll().size}",
            )
            sourceId
        }.also { result ->
            result.onFailure { error ->
                ShareFlowLogger.e("Index", "failed: ${error.message}", error)
            }
        }
    }

    suspend fun indexText(text: String): Result<Int> = withContext(ioDispatcher) {
        indexContent(
            IndexRequest(
                text = text,
                sourceType = SourceType.MANUAL,
            ),
        ).map { sourceId ->
            if (sourceId == 0L) 0 else sourceRegistry.get(sourceId)?.chunkCount ?: 0
        }
    }

    suspend fun listSources(): Result<List<SourceRecord>> = withContext(ioDispatcher) {
        runCatching {
            ensureInitialized()
            val registrySources = sourceRegistry.listAll()
            if (registrySources.isNotEmpty()) {
                return@runCatching registrySources
            }
            val fromAppSearch = listSourcesFromAppSearch()
            fromAppSearch.forEach { sourceRegistry.save(it) }
            fromAppSearch
        }
    }

    suspend fun getSource(sourceId: Long): SourceRecord? = withContext(ioDispatcher) {
        sourceRegistry.get(sourceId) ?: runCatching {
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

    suspend fun getSourceDetail(sourceId: Long): Result<SourceDetail> = withContext(ioDispatcher) {
        runCatching {
            ensureInitialized()
            val source = getSource(sourceId)
                ?: error("주입 기록을 찾을 수 없습니다")

            val chunks = if (source.chunkCount <= 0) {
                emptyList()
            } else {
                val chunkIds = (0 until source.chunkCount).map { "${sourceId}_$it" }
                val session = sessionFuture.await()
                val request = GetByDocumentIdRequest.Builder(TextChunkDocument.NAMESPACE)
                    .addIds(chunkIds)
                    .build()
                val result = session.getByDocumentIdAsync(request).await()
                chunkIds.mapNotNull { chunkId ->
                    result.getSuccesses()[chunkId]
                        ?.toDocumentClass(TextChunkDocument::class.java)
                }
                    .sortedBy { it.chunkIndex }
                    .map { it.content }
            }

            val fullText = if (chunks.isNotEmpty()) {
                chunks.joinToString("\n\n")
            } else {
                source.previewText
            }

            SourceDetail(
                source = source,
                fullText = fullText,
                chunks = chunks,
            )
        }
    }

    suspend fun search(query: String): Result<List<ChunkSearchResult>> = withContext(ioDispatcher) {
        runCatching {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@runCatching emptyList()

            ensureInitialized()
            val session = sessionFuture.await()
            val searchSpec = SearchSpec.Builder()
                .addFilterSchemas(TextChunkDocument.SCHEMA_TYPE)
                .addFilterNamespaces(TextChunkDocument.NAMESPACE)
                .setRankingStrategy(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE)
                .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
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

    private suspend fun listSourcesFromAppSearch(): List<SourceRecord> {
        val session = sessionFuture.await()
        val searchSpec = SearchSpec.Builder()
            .addFilterSchemas(SourceDocument.SCHEMA_TYPE)
            .addFilterNamespaces(SourceDocument.NAMESPACE)
            .setRankingStrategy(SearchSpec.RANKING_STRATEGY_CREATION_TIMESTAMP)
            .build()

        val searchResults = session.search("*", searchSpec)
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
        return records.sortedByDescending { it.creationTimestampMillis }
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

    private fun mapSearchResult(searchResult: SearchResult): ChunkSearchResult {
        val document = searchResult.genericDocument.toDocumentClass(TextChunkDocument::class.java)
        val source = sourceRegistry.get(document.sourceId)
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
