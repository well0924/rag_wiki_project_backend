package com.example.wiki_project.domain.member

import com.example.wiki_project.member.domain.model.MemberModel
import com.example.wiki_project.member.domain.model.Role
import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetails
import com.example.wiki_project.member.infrastructure.security.jwt.JwtTokenProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken

class JwtTokenProviderTest {

    private val jwtTokenProvider = JwtTokenProvider(
        secretKeyString = "test-secret-key-for-jwt-token-provider-must-be-long-enough",
        accessExpiration = 1_800_000L,
        refreshExpiration = 1_209_600_000L
    )

    private val email = "test@company.com"

    private fun createAccessToken(): String {
        val member = MemberModel(id = 1L, email = email, password = "encrypted", userName = "홍길동", role = Role.ROLE_MEMBER)
        val userDetails = CustomUserDetails(member = member, groupIds = listOf(1L), isAdmin = false)
        return jwtTokenProvider.createAccessToken(
            UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        )
    }

    @Test
    @DisplayName("Access Token은 API 인증용으로만 판별되고 재발급에는 사용할 수 없다")
    fun accessTokenType() {
        val accessToken = createAccessToken()

        assertThat(jwtTokenProvider.isAccessToken(accessToken)).isTrue()
        assertThat(jwtTokenProvider.isRefreshToken(accessToken)).isFalse()
        assertThat(jwtTokenProvider.getEmail(accessToken)).isEqualTo(email)
    }

    @Test
    @DisplayName("Refresh Token은 재발급용으로만 판별되고 API 인증에는 사용할 수 없다")
    fun refreshTokenType() {
        val refreshToken = jwtTokenProvider.createRefreshToken(email)

        assertThat(jwtTokenProvider.isRefreshToken(refreshToken)).isTrue()
        assertThat(jwtTokenProvider.isAccessToken(refreshToken)).isFalse()
        assertThat(jwtTokenProvider.getEmail(refreshToken)).isEqualTo(email)
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 유효하지 않다")
    fun invalidSignature() {
        val otherProvider = JwtTokenProvider(
            secretKeyString = "another-secret-key-for-jwt-token-provider-must-be-long",
            accessExpiration = 1_800_000L,
            refreshExpiration = 1_209_600_000L
        )

        assertThat(jwtTokenProvider.validateToken(otherProvider.createRefreshToken(email))).isFalse()
    }
}
