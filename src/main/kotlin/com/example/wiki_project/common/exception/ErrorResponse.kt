package com.example.wiki_project.common.exception

import java.time.LocalDateTime

data class ErrorResponse(
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val status: Int,
    val error: String,
    val message: String
) {
    companion object {
        fun of(errorCode: ErrorCode): ErrorResponse = ErrorResponse(
            status = errorCode.status.value(),
            error = errorCode.status.name,
            message = errorCode.message
        )
    }
}