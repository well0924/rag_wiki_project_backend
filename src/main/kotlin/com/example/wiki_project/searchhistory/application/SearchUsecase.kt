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
     * 전체 RAG 자연어 지능형 검색 흐름 관제 파이프라인
     */
    suspend fun executeSearch(
        memberId: Long,
        query: String,
        userGroupIds: List<Long>,
        isAdmin: Boolean
    ): SearchResponse = withContext(Dispatchers.IO) { // 코루틴 격리 스레드 가동

        // 1. Python AI 서버 연동 및 벡터 스캔 답변 추출
        val aiResponse = pythonAiClient.searchFromRAG(query, userGroupIds, isAdmin)

        // 2. 우리가 방금 격파한 품질 측정 로그 적재 엔진 가동 ( search_history 저장 )
        val historyId = searchHistoryService.recordSearchHistory(
            memberId = memberId,
            query = query,
            answer = aiResponse.answer,
            referencedChunkIds = aiResponse.referencedChunkIds
        )

        // 3. 최종 자연어 답변 + 피드백용 historyId 프론트엔드로 즉시 서빙 반환
        SearchResponse(
            historyId = historyId,
            answer = aiResponse.answer,
            referencedChunkIds = aiResponse.referencedChunkIds,
            sources = aiResponse.sources.map { SearchSource(it.documentId, it.title, it.similarity) }
        )
    }
}