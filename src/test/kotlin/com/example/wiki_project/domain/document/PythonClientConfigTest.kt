package com.example.wiki_project.domain.document

import com.example.wiki_project.document.infrastructure.client.PythonClientConfig
import com.example.wiki_project.member.infrastructure.security.filter.InternalApiKeyFilter
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class PythonClientConfigTest {

    private val internalApiKey = "test-internal-key"
    private val capturedRequests = mutableListOf<ClientRequest>()

    // 실제 FastAPI 서버 대신 요청을 가로채 기록하고 고정 응답을 돌려주는 가짜 ExchangeFunction
    private fun clientReturning(body: String): PythonClientConfig {
        val builder = WebClient.builder().exchangeFunction { request ->
            capturedRequests.add(request)
            Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build()
            )
        }
        return PythonClientConfig(builder, "http://ai-server", internalApiKey)
    }

    @Test
    @DisplayName("RAG 검색 요청에는 내부 API 키 헤더가 첨부된다")
    fun searchSendsInternalKey() = runTest {
        val client = clientReturning("""{"answer":"답변","referencedChunkIds":[1,2]}""")

        val response = client.searchFromRAG("질문", listOf(1L), false)

        val request = capturedRequests.single()
        assertThat(request.url().path).isEqualTo("/api/v1/rag/search")
        assertThat(request.headers().getFirst(InternalApiKeyFilter.HEADER)).isEqualTo(internalApiKey)
        assertThat(response.answer).isEqualTo("답변")
        assertThat(response.referencedChunkIds).containsExactly("1", "2")
    }

    @Test
    @DisplayName("벡터 청크 파기 요청은 FastAPI 실제 경로(/api/v1/rag/vector/clear)로 키와 함께 전송된다")
    fun clearVectorUsesRagPrefixAndKey() = runTest {
        val client = clientReturning("""{"success":true,"message":"ok"}""")

        client.clearVectorChunks(45L)

        val request = capturedRequests.single()
        assertThat(request.method()).isEqualTo(HttpMethod.DELETE)
        assertThat(request.url().path).isEqualTo("/api/v1/rag/vector/clear/45")
        assertThat(request.headers().getFirst(InternalApiKeyFilter.HEADER)).isEqualTo(internalApiKey)
    }
}
