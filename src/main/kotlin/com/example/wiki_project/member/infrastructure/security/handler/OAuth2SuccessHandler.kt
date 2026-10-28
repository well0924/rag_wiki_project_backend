package com.example.wiki_project.member.infrastructure.security.handler

import com.example.wiki_project.member.application.dto.TokenResponse
import com.example.wiki_project.member.infrastructure.security.OAuth2LoginCodeRepository
import com.example.wiki_project.member.infrastructure.security.RedisTokenRepository
import com.example.wiki_project.member.infrastructure.security.auth.CustomAuthenticationManager
import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetails
import com.example.wiki_project.member.infrastructure.security.jwt.JwtTokenProvider
import com.example.wiki_project.member.infrastructure.security.oauth.GoogleOAuth2AuthenticationToken

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.nio.charset.StandardCharsets

@Component
class OAuth2SuccessHandler(
    private val authenticationManager: CustomAuthenticationManager,
    private val jwtTokenProvider: JwtTokenProvider,
    private val tokenRepository: RedisTokenRepository,
    private val loginCodeRepository: OAuth2LoginCodeRepository,
    @Value("\${app.frontend-url:http://localhost:3000}") private val frontendUrl: String
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        // CustomOAuth2UserService 가 반환한 구글 사용자 정보
        val oAuth2User = authentication.principal as OAuth2User

        // 검증을 마친 이메일 / 이름
        val email = oAuth2User.attributes["extractedEmail"] as String
        val name = oAuth2User.attributes["extractedName"] as String

        // 1. 아직 인증 전 상태인 구글 전용 인증 토큰 생성
        val googleToken = GoogleOAuth2AuthenticationToken(email, name)

        // 2. 인증 매니저에 위임
        // -> GoogleOAuth2AuthenticationProvider 가 처리
        // -> 신규 회원은 자동 가입(JIT), 탈퇴 회원은 로그인 차단
        val authenticatedResult = authenticationManager.authenticate(googleToken)

        // 3. 인증 완료된 사용자 정보 (CustomUserDetails)
        val userDetails = authenticatedResult.principal as CustomUserDetails

        // 4. 서비스 JWT (Access / Refresh) 발급
        val accessToken = jwtTokenProvider.createAccessToken(authenticatedResult)
        val refreshToken = jwtTokenProvider.createRefreshToken(userDetails.username)

        // 5. Refresh Token 을 Redis 에 저장 (재발급 검증, 로그아웃 / 추방 시 무효화)
        tokenRepository.saveRefreshToken(userDetails.username, refreshToken)

        // 6. 토큰은 Redis 에 보관하고 프론트엔드 콜백 주소에는 30초짜리 일회용 코드만 전달
        // - URL 에 토큰을 직접 실으면 브라우저 기록, 프록시/서버 접근 로그, Referer 헤더로 노출됨
        // - 프론트는 POST /api/v1/auth/oauth2/token 으로 코드를 토큰 세트로 교환
        val code = loginCodeRepository.issue(TokenResponse(accessToken, refreshToken))
        val targetUrl = UriComponentsBuilder.fromUriString("$frontendUrl/oauth2/redirect")
            .queryParam("code", code)
            .build()
            .encode(StandardCharsets.UTF_8)
            .toUriString()

        clearAuthenticationAttributes(request)
        redirectStrategy.sendRedirect(request, response, targetUrl)
    }
}