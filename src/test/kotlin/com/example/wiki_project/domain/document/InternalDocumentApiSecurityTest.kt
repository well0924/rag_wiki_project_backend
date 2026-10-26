package com.example.wiki_project.domain.document

import com.example.wiki_project.member.infrastructure.security.filter.InternalApiKeyFilter
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class InternalDocumentApiSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Value("\${internal.api-key:local-internal-api-key}")
    private lateinit var internalApiKey: String

    // 존재하지 않는 문서 ID → 보안 필터를 통과하면 404, 막히면 401
    private val statusUrl = "/api/v1/internal/documents/999999999/status?status=COMPLETED"

    @Test
    @DisplayName("내부 API 키 없이 문서 상태 변경을 호출하면 401로 차단된다")
    fun rejectWithoutKey() {
        mockMvc.perform(patch(statusUrl))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("틀린 내부 API 키로 호출하면 401로 차단된다")
    fun rejectWithWrongKey() {
        mockMvc.perform(patch(statusUrl).header(InternalApiKeyFilter.HEADER, "wrong-key"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("올바른 내부 API 키로 호출하면 보안 필터를 통과해 서비스 로직(문서 미존재 404)까지 도달한다")
    fun allowWithValidKey() {
        mockMvc.perform(patch(statusUrl).header(InternalApiKeyFilter.HEADER, internalApiKey))
            .andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("내부 API 키는 /api/v1/internal/** 이외의 경로에서는 인증 수단으로 인정되지 않는다")
    fun keyNotValidOutsideInternalPath() {
        mockMvc.perform(
            patch("/api/v1/documents/999999999/status?status=COMPLETED")
                .header(InternalApiKeyFilter.HEADER, internalApiKey)
        ).andExpect(status().isUnauthorized)
    }
}
