package com.example.wiki_project.member.infrastructure.security.handler

import com.example.wiki_project.member.infrastructure.security.RedisTokenRepository
import com.example.wiki_project.member.infrastructure.security.auth.CustomAuthenticationManager
import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetails
import com.example.wiki_project.member.infrastructure.security.jwt.JwtTokenProvider
import com.example.wiki_project.member.infrastructure.security.oauth.GoogleOAuth2AuthenticationToken

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
    private val tokenRepository: RedisTokenRepository
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        // OAuth2 기본 필터가 구글로부터 받아온 로우 데이터 객체
        val oAuth2User = authentication.principal as OAuth2User

        // CustomOAuth2UserService에서 파싱해 둔 안전한 String 데이터 추출
        val email = oAuth2User.attributes["extractedEmail"] as String
        val name = oAuth2User.attributes["extractedName"] as String

        // 1. 아직 인증 전 상태인 구글 전용 인증 토큰 생성
        val googleToken = GoogleOAuth2AuthenticationToken(email, name)

        // 2. 핵심: 매니저에게 인증 대행 요청
        // -> 매니저 내부에 위임된 Provider 체인 중 GoogleOAuth2AuthenticationProvider가 낚아채서
        // -> 자동 회원가입(JIT)을 시키거나, Soft Delete(탈퇴 계정) 검증을 원자적으로 수행함!
        val authenticatedResult = authenticationManager.authenticate(googleToken)

        // 3. 무사히 통과했다면 인증이 완료된 최종 Principal(CustomUserDetails) 확보
        val userDetails = authenticatedResult.principal as CustomUserDetails

        // 4. 우리 시스템 전용 명품 JWT 세트(Access / Refresh) 생성
        val accessToken = jwtTokenProvider.createAccessToken(authenticatedResult)
        val refreshToken = jwtTokenProvider.createRefreshToken(userDetails.username)

        // 5. 탈퇴 및 세션 강제 추방 방어선을 위해 Redis에 리프레시 토큰 안전하게 적재
        tokenRepository.saveRefreshToken(userDetails.username, refreshToken)

        // 6. 프론트엔드(React 등) 콜백 주소에 토큰을 파라미터로 심어서 우아하게 토스
        val targetUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/oauth2/redirect")
            .queryParam("accessToken", accessToken)
            .queryParam("refreshToken", refreshToken)
            .build()
            .encode(StandardCharsets.UTF_8)
            .toUriString()

        clearAuthenticationAttributes(request)
        redirectStrategy.sendRedirect(request, response, targetUrl)
    }
}