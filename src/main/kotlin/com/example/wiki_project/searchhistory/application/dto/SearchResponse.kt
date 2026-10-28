package com.example.wiki_project.searchhistory.application.dto

/**
 * RAG 검색 응답
 * - historyId: 프론트에서 👍/👎 피드백(PATCH /search-histories/{historyId}/feedback) 전송 시 사용
 * - sources: 답변 근거 문서 목록 (사용자가 원문으로 답변을 검증할 수 있도록 노출, 비어 있으면 근거 없음)
 */
data class SearchResponse(
    val historyId: Long,
    val answer: String,
    val referencedChunkIds: List<String>,
    val sources: List<SearchSource> = emptyList()
)

data class SearchSource(
    val documentId: Long,
    val title: String,
    val similarity: Double
)
