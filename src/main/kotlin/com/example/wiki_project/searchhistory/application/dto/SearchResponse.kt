package com.example.wiki_project.searchhistory.application.dto

/**
 * RAG 검색 응답
 * - historyId: 프론트에서 👍/👎 피드백(PATCH /search-histories/{historyId}/feedback) 전송 시 사용
 */
data class SearchResponse(
    val historyId: Long,
    val answer: String,
    val referencedChunkIds: List<String>
)
