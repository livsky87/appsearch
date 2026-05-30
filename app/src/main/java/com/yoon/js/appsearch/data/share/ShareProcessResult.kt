package com.yoon.js.appsearch.data.share

sealed interface ShareProcessResult {
    data class Success(val sourceId: Long) : ShareProcessResult
    data class Failure(val message: String) : ShareProcessResult
}
