package com.example.wiki_project.document.infrastructure.client

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.document.infrastructure.client.dto.PythonSearchRequest
import com.example.wiki_project.document.infrastructure.client.dto.PythonSearchResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Configuration
class PythonClientConfig(
    private val webClientBuilder: WebClient.Builder,
    @Value("\${ai.fastapi.url:http://localhost:8000}") private val aiServerUrl: String
) {
    private val client by lazy {
        webClientBuilder.baseUrl(aiServerUrl).build()
    }

    /**
     * Python RAG 엔진에 검색 질의 요청 (코루틴 suspend 적용)
     */
    suspend fun searchFromRAG(query: String, userGroupIds: List<Long>, isAdmin: Boolean): PythonSearchResponse {
        val requestBody = PythonSearchRequest(query = query, userGroupIds = userGroupIds, isAdmin = isAdmin)

        return try {
            client.post()
                .uri("/api/v1/rag/search")
                .bodyValue(requestBody)
                .retrieve()
                .awaitBody<PythonSearchResponse>() // 리액티브 WebClient를 코루틴 suspend 사상으로 전환하는 마법의 키!
        } catch (e: Exception) {
            // AI 서버 다운 등 인프라 장애 발생 시 도메인 격리를 위한 예외 치환
            throw BusinessException(ErrorCode.AI_SERVER_COMMUNICATION_FAILED)
        }
    }
}