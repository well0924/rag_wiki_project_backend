package com.example.wiki_project.member.infrastructure.security.handler

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class OAuth2FailureHandler(
    @Value("\${app.frontend-url:http://localhost:3000}") private val frontendUrl: String
): SimpleUrlAuthenticationFailureHandler() {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: org.springframework.security.core.AuthenticationException?
    ) {
        // 인증 실패 사유 (허용되지 않은 도메인, 탈퇴 계정 등)
        val errorMessage = exception?.message ?: "소셜 로그인 인증에 실패했습니다."

        // 한글 메시지를 쿼리 파라미터로 전달하기 위해 URL 인코딩
        val encodedErrorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString())

        // 프론트 로그인 화면으로 실패 여부와 사유를 담아 리다이렉트
        val targetUrl = UriComponentsBuilder.fromUriString("$frontendUrl/login")
            .queryParam("error", true)
            .queryParam("message", encodedErrorMessage)
            .build()
            .toUriString()

        redirectStrategy.sendRedirect(request, response, targetUrl)
    }
}