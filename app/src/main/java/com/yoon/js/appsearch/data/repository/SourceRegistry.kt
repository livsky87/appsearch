package com.yoon.js.appsearch.data.repository

import android.content.Context
import com.yoon.js.appsearch.data.share.ShareFlowLogger
import com.yoon.js.appsearch.domain.model.SourceRecord
import com.yoon.js.appsearch.domain.model.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class SourceRegistry @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(record: SourceRecord) {
        val updated = loadAll()
            .filterNot { it.sourceId == record.sourceId }
            .toMutableList()
        updated.add(0, record)
        persist(updated)
        ShareFlowLogger.d(
            "Registry",
            "save sourceId=${record.sourceId} total=${updated.size} " +
                "title=${ShareFlowLogger.preview(record.title, 60)} chunks=${record.chunkCount}",
        )
    }

    fun listAll(): List<SourceRecord> {
        val all = loadAll().sortedByDescending { it.creationTimestampMillis }
        ShareFlowLogger.d("Registry", "listAll count=${all.size}")
        return all
    }

    fun get(sourceId: Long): SourceRecord? {
        return loadAll().firstOrNull { it.sourceId == sourceId }
    }

    private fun loadAll(): List<SourceRecord> {
        val raw = prefs.getString(KEY_SOURCES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toSourceRecord())
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(records: List<SourceRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(record.toJson())
        }
        prefs.edit().putString(KEY_SOURCES, array.toString()).apply()
    }

    private fun SourceRecord.toJson(): JSONObject {
        return JSONObject()
            .put("sourceId", sourceId)
            .put("sourceType", sourceType.code)
            .put("title", title)
            .put("url", url)
            .put("imageUrl", imageUrl)
            .put("previewText", previewText)
            .put("creationTimestampMillis", creationTimestampMillis)
            .put("chunkCount", chunkCount)
    }

    private fun JSONObject.toSourceRecord(): SourceRecord {
        return SourceRecord(
            sourceId = getLong("sourceId"),
            sourceType = SourceType.fromCode(getLong("sourceType")),
            title = getString("title"),
            url = getString("url"),
            imageUrl = getString("imageUrl"),
            previewText = getString("previewText"),
            creationTimestampMillis = getLong("creationTimestampMillis"),
            chunkCount = getInt("chunkCount"),
        )
    }

    companion object {
        private const val PREFS_NAME = "source_registry"
        private const val KEY_SOURCES = "sources"
    }
}
