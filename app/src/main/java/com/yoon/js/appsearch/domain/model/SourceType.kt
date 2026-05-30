package com.yoon.js.appsearch.domain.model

enum class SourceType(val code: Long) {
    MANUAL(0),
    SHARE_TEXT(1),
    SHARE_WEB(2),
    ;

    companion object {
        fun fromCode(code: Long): SourceType =
            entries.find { it.code == code } ?: MANUAL
    }
}
