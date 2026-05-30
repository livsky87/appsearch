package com.yoon.js.appsearch.data.share

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ShareCoordinator @Inject constructor() {
    private val _pendingShare = MutableStateFlow<SharePayload?>(null)
    val pendingShare: StateFlow<SharePayload?> = _pendingShare.asStateFlow()

    fun enqueue(payload: SharePayload) {
        _pendingShare.value = payload
    }

    fun consume(): SharePayload? {
        val payload = _pendingShare.value
        _pendingShare.value = null
        return payload
    }
}
