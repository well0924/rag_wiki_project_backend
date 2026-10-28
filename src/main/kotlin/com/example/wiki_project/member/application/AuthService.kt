package com.example.wiki_project.member.application

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.member.application.dto.LoginCommand
import com.example.wiki_project.member.application.dto.TokenResponse
import com.example.wiki_project.member.infrastructure.security.OAuth2LoginCodeRepository
import com.example.wiki_project.member.infrastructure.security.RedisTokenRepository
import com.example.wiki_project.member.infrastructure.security.UserCacheRepository
import com.example.wiki_project.member.infrastructure.security.auth.CustomAuthenticationManager
import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetailsService
import com.example.wiki_project.member.infrastructure.security.jwt.JwtTokenProvider
import io.jsonwebtoken.JwtException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val authenticationManager: CustomAuthenticationManager,
    private val jwtTokenProvider: JwtTokenProvider,
    private val tokenRepository: RedisTokenRepository,
    private val userCacheRepository: UserCacheRepository,
    private val userDetailsService: CustomUserDetailsService,
    private val oAuth2LoginCodeRepository: OAuth2LoginCodeRepository
) {

    /**
     * 로그인
     */
    fun login(command: LoginCommand): TokenResponse {
        val authenticationToken = UsernamePasswordAuthenticationToken(command.email, command.password)
        val authentication = authenticationManager.authenticate(authenticationToken)

        val accessToken = jwtTokenProvider.createAccessToken(authentication)
        val refreshToken = jwtTokenProvider.createRefreshToken(command.email)

        tokenRepository.saveRefreshToken(command.email, refreshToken)

        return TokenResponse(accessToken, refreshToken)
    }

    /**
     * 토큰 재발급(Reissue)
     */
    fun reissue(refreshToken: String): TokenResponse {
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw JwtException("유효하지 않은 Refresh Token입니다.")
        }

        val email = jwtTokenProvider.getEmail(refreshToken)
        val savedRefreshToken = tokenRepository.getRefreshToken(email)

        // 레디스에 토큰이 없거나 날아온 토큰과 다르면 탈취로 간주
        if (savedRefreshToken == null || savedRefreshToken != refreshToken) {
            tokenRepository.deleteRefreshToken(email)
            userCacheRepository.deleteUserCache(email)
            throw JwtException("만료되었거나 일치하지 않는 Refresh Token입니다.")
        }

        // Refresh Token에는 권한 정보가 없으므로 캐시/DB에서 최신 role, groupIds를 다시 적재
        val userDetails = userDetailsService.loadUserByUsername(email)
        if (!userDetails.isEnabled) {
            tokenRepository.deleteRefreshToken(email)
            throw JwtException("탈퇴 처리된 계정입니다.")
        }
        val authentication = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)

        val newAccessToken = jwtTokenProvider.createAccessToken(authentication)
        val newRefreshToken = jwtTokenProvider.createRefreshToken(email)

        tokenRepository.saveRefreshToken(email, newRefreshToken)

        return TokenResponse(newAccessToken, newRefreshToken)
    }

    /**
     * 소셜 로그인 일회용 코드 → 토큰 세트 교환 (코드는 1회만 유효)
     */
    fun exchangeOAuth2LoginCode(code: String): TokenResponse {
        return oAuth2LoginCodeRepository.consume(code)
            ?: throw BusinessException(ErrorCode.INVALID_OAUTH2_LOGIN_CODE)
    }

    /**
     * 로그아웃 유스케이스
     */
    fun logout(accessToken: String, email: String) {
        tokenRepository.deleteRefreshToken(email)
        userCacheRepository.deleteUserCache(email)

        val remainExpirationMs = jwtTokenProvider.getExpiration(accessToken)
        if (remainExpirationMs > 0) {
            tokenRepository.addToBlacklist(accessToken, remainExpirationMs)
        }
        SecurityContextHolder.clearContext()
    }
}
