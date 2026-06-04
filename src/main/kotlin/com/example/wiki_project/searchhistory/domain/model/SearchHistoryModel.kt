package com.example.wiki_project.searchhistory.domain.model

import java.time.LocalDateTime

data class SearchHistoryModel(
    val id: Long? = null,
    val memberId: Long,
    val query: String,
    val answer: String?,
    val referencedChunkIds: List<String> = emptyList(),
    val feedback: Boolean? = null, // 추천(true) / 비추천(false) / 미선택(null)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 비즈니스 규칙: RAG 답변 품질 피드백 토글/업데이트
     */
    fun updateFeedback(isPositive: Boolean): SearchHistoryModel {
        return copy(feedback = isPositive)
    }
}