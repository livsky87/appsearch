package com.yoon.js.appsearch.data.share

import android.util.Log

object ShareFlowLogger {
    const val TAG = "AppSearchShare"

    fun d(step: String, message: String) {
        log { Log.d(TAG, "[$step] $message") }
    }

    fun w(step: String, message: String, throwable: Throwable? = null) {
        log {
            if (throwable != null) {
                Log.w(TAG, "[$step] $message", throwable)
            } else {
                Log.w(TAG, "[$step] $message")
            }
        }
    }

    fun e(step: String, message: String, throwable: Throwable? = null) {
        log {
            if (throwable != null) {
                Log.e(TAG, "[$step] $message", throwable)
            } else {
                Log.e(TAG, "[$step] $message")
            }
        }
    }

    private inline fun log(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // JVM unit tests may not mock android.util.Log
        }
    }

    fun preview(text: String, maxLength: Int = 120): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return "(empty)"
        return if (normalized.length <= maxLength) {
            normalized
        } else {
            "${normalized.take(maxLength)}…(len=${normalized.length})"
        }
    }
}
