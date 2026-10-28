package com.example.wiki_project.searchhistory.application

import com.example.wiki_project.document.infrastructure.client.PythonClientConfig
import com.example.wiki_project.searchhistory.application.dto.SearchResponse
import com.example.wiki_project.searchhistory.application.dto.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

@Service
class SearchUsecase(
    private val pythonAiClient: PythonClientConfig,
    private val searchHistoryService: SearchHistoryService
) {

    /**
     * RAG 검색 흐름: AI 서버 검색 → 검색 기록 저장 → 응답 구성
     */
    suspend fun executeSearch(
        memberId: Long,
        query: String,
        userGroupIds: List<Long>,
        isAdmin: Boolean
    ): SearchResponse = withContext(Dispatchers.IO) { // 검색 기록 저장(블로킹 DB 작업)이 포함되므로 IO 디스패처에서 실행

        // 1. AI 서버에 권한 필터 검색 + 답변 생성 요청
        val aiResponse = pythonAiClient.searchFromRAG(query, userGroupIds, isAdmin)

        // 2. 검색 기록 저장 (피드백 / 품질 모니터링용)
        val historyId = searchHistoryService.recordSearchHistory(
            memberId = memberId,
            query = query,
            answer = aiResponse.answer,
            referencedChunkIds = aiResponse.referencedChunkIds
        )

        // 3. 답변 + 출처 + 피드백용 historyId 반환
        SearchResponse(
            historyId = historyId,
            answer = aiResponse.answer,
            referencedChunkIds = aiResponse.referencedChunkIds,
            sources = aiResponse.sources.map { SearchSource(it.documentId, it.title, it.similarity) }
        )
    }
}