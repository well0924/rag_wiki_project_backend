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
        // 프로바이더가 뱉은 에러 메시지 맑게 추출
        val errorMessage = exception?.message ?: "소셜 로그인 인증에 실패했습니다."

        // 프론트엔드 브라우저 쿼리 파라미터가 한글을 깨짐 없이 먹을 수 있게 인코딩
        val encodedErrorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString())

        // 프론트엔드의 로그인 화면으로 에러 마킹 및 알림용 메시지 얹어서 강제 리다이렉트
        val targetUrl = UriComponentsBuilder.fromUriString("$frontendUrl/login")
            .queryParam("error", true)
            .queryParam("message", encodedErrorMessage)
            .build()
            .toUriString()

        redirectStrategy.sendRedirect(request, response, targetUrl)
    }
}