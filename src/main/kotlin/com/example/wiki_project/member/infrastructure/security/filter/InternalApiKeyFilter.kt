package com.example.wiki_project.member.infrastructure.security.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * FastAPI(AI 서버) → Spring Boot 내부 역호출 인증 필터
 * - /api/v1/internal/ 하위 요청에 한해 X-Internal-Api-Key 공유 키를 검증
 * - 키가 일치하면 ROLE_INTERNAL 권한을 부여하고, 불일치 시 인증 없이 통과시켜 EntryPoint(401)가 처리
 */
@Component
class InternalApiKeyFilter(
    @Value("\${internal.api-key:local-internal-api-key}") private val internalApiKey: String
) : OncePerRequestFilter() {

    companion object {
        const val HEADER = "X-Internal-Api-Key"
        const val INTERNAL_PATH_PREFIX = "/api/v1/internal/"
        const val ROLE_INTERNAL = "ROLE_INTERNAL"
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return !request.requestURI.startsWith(INTERNAL_PATH_PREFIX)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestKey = request.getHeader(HEADER)

        if (requestKey != null && isValidKey(requestKey)) {
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                "internal-ai-server", null, listOf(SimpleGrantedAuthority(ROLE_INTERNAL))
            )
        }

        filterChain.doFilter(request, response)
    }

    // 타이밍 공격 방지를 위한 상수 시간 비교
    private fun isValidKey(requestKey: String): Boolean {
        return MessageDigest.isEqual(
            requestKey.toByteArray(StandardCharsets.UTF_8),
            internalApiKey.toByteArray(StandardCharsets.UTF_8)
        )
    }
}
