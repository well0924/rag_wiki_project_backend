package com.example.wiki_project.domain.member

import com.example.wiki_project.member.application.dto.TokenResponse
import com.example.wiki_project.member.domain.model.MemberModel
import com.example.wiki_project.member.domain.model.Role
import com.example.wiki_project.member.infrastructure.security.OAuth2LoginCodeRepository
import com.example.wiki_project.member.infrastructure.security.RedisTokenRepository
import com.example.wiki_project.member.infrastructure.security.auth.CustomAuthenticationManager
import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetails
import com.example.wiki_project.member.infrastructure.security.handler.OAuth2SuccessHandler
import com.example.wiki_project.member.infrastructure.security.jwt.JwtTokenProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.DefaultOAuth2User

@ExtendWith(MockitoExtension::class)
class OAuth2SuccessHandlerTest {

    @Mock private lateinit var authenticationManager: CustomAuthenticationManager
    @Mock private lateinit var jwtTokenProvider: JwtTokenProvider
    @Mock private lateinit var tokenRepository: RedisTokenRepository
    @Mock private lateinit var loginCodeRepository: OAuth2LoginCodeRepository

    @Test
    @DisplayName("구글 로그인 성공 시 토큰 대신 일회용 코드만 담아 프론트로 리다이렉트한다 (URL 토큰 노출 방지)")
    fun redirectWithOneTimeCodeInsteadOfTokens() {
        // given
        val handler = OAuth2SuccessHandler(
            authenticationManager, jwtTokenProvider, tokenRepository, loginCodeRepository, "http://localhost:3000"
        )
        val oAuth2User = DefaultOAuth2User(
            listOf(SimpleGrantedAuthority("ROLE_MEMBER")),
            mapOf("sub" to "1", "extractedEmail" to "tester@gmail.com", "extractedName" to "홍길동"),
            "sub"
        )
        val userDetails = CustomUserDetails(
            member = MemberModel(id = 1L, email = "tester@gmail.com", password = "x", userName = "홍길동", role = Role.ROLE_MEMBER),
            groupIds = listOf(10L),
            isAdmin = false
        )
        val authenticated = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        whenever(authenticationManager.authenticate(any())).thenReturn(authenticated)
        whenever(jwtTokenProvider.createAccessToken(authenticated)).thenReturn("access-token")
        whenever(jwtTokenProvider.createRefreshToken("tester@gmail.com")).thenReturn("refresh-token")
        whenever(loginCodeRepository.issue(TokenResponse("access-token", "refresh-token"))).thenReturn("one-time-code")

        val response = MockHttpServletResponse()

        // when
        handler.onAuthenticationSuccess(
            MockHttpServletRequest(),
            response,
            OAuth2AuthenticationToken(oAuth2User, oAuth2User.authorities, "google")
        )

        // then
        assertThat(response.redirectedUrl).isEqualTo("http://localhost:3000/oauth2/redirect?code=one-time-code")
        assertThat(response.redirectedUrl).doesNotContain("access-token", "refresh-token")
        verify(tokenRepository).saveRefreshToken("tester@gmail.com", "refresh-token")
    }
}
