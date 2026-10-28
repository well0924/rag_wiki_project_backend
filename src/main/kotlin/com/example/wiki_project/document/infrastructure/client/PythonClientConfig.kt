package com.example.wiki_project.document.infrastructure.client

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.document.infrastructure.client.dto.PythonSearchRequest
import com.example.wiki_project.document.infrastructure.client.dto.PythonSearchResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import com.example.wiki_project.member.infrastructure.security.filter.InternalApiKeyFilter
import io.netty.channel.ChannelOption
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody

@Component
class PythonClientConfig(
    private val webClientBuilder: WebClient.Builder,
    @Value("\${ai.fastapi.url:http://localhost:8000}") private val aiServerUrl: String,
    // FastAPI 측 INTERNAL_API_KEY와 동일한 서버 간 공유 키
    @Value("\${internal.api-key:local-internal-api-key}") private val internalApiKey: String,
    // AI 서버 장애 시 요청 스레드가 무한 대기하지 않도록 연결/응답 타임아웃 지정
    @Value("\${ai.fastapi.connect-timeout:3s}") private val connectTimeout: Duration = Duration.ofSeconds(3),
    @Value("\${ai.fastapi.response-timeout:60s}") private val responseTimeout: Duration = Duration.ofSeconds(60)
) {
    private val client by lazy {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout.toMillis().toInt())
            .responseTimeout(responseTimeout)

        webClientBuilder
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .baseUrl(aiServerUrl)
            .defaultHeader(InternalApiKeyFilter.HEADER, internalApiKey)
            .build()
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
                .awaitBody<PythonSearchResponse>() // Mono 응답을 코루틴 suspend 방식으로 대기
        } catch (e: Exception) {
            // AI 서버 장애를 도메인 예외로 변환하여 호출 측에 전달
            throw BusinessException(ErrorCode.AI_SERVER_COMMUNICATION_FAILED)
        }
    }

    /**
     * 영구 삭제 배치에서 삭제된 문서의 벡터 청크 삭제를 AI 서버에 요청
     */
    suspend fun clearVectorChunks(documentId: Long) {
        try {
            client.delete()
                .uri("/api/v1/rag/vector/clear/$documentId")
                .retrieve()
                .awaitBodilessEntity() // 응답 바디는 사용하지 않고 2xx 여부만 코루틴으로 수신
        } catch (e: Exception) {
            // 배치 작업 중 AI 서버 통신 장애가 나면 정합성이 깨지므로 로그를 남기고 예외 전파
            throw BusinessException(ErrorCode.AI_SERVER_COMMUNICATION_FAILED)
        }
    }
}