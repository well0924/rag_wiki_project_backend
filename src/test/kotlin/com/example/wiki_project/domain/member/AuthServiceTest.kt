package com.example.wiki_project.domain.member

import com.example.wiki_project.member.application.AuthService
import com.example.wiki_project.member.application.dto.LoginCommand
import com.example.wiki_project.member.domain.model.MemberModel
import com.example.wiki_project.member.domain.model.Role
import com.example.wiki_project.member.infrastructure.MemberRepository
import com.example.wiki_project.member.infrastructure.UserGroupMemberRepository
import com.example.wiki_project.member.infrastructure.UserGroupRepository
import com.example.wiki_project.member.infrastructure.security.RedisTokenRepository
import com.example.wiki_project.member.infrastructure.security.UserCacheRepository
import com.example.wiki_project.member.infrastructure.security.auth.CustomAuthenticationManager
import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetails
import com.example.wiki_project.member.infrastructure.security.jwt.JwtTokenProvider
import io.jsonwebtoken.JwtException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.assertThat

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder

@ExtendWith(MockitoExtension::class)
class AuthServiceTest {

    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var userGroupRepository: UserGroupRepository
    @Mock private lateinit var userGroupMemberRepository: UserGroupMemberRepository
    @Mock private lateinit var authenticationManager: CustomAuthenticationManager
    @Mock private lateinit var jwtTokenProvider: JwtTokenProvider
    @Mock private lateinit var tokenRepository: RedisTokenRepository
    @Mock private lateinit var userCacheRepository: UserCacheRepository
    @Mock private lateinit var passwordEncoder: PasswordEncoder

    @InjectMocks
    private lateinit var authService: AuthService

    @Test
    @DisplayName("올바른 이메일과 비밀번호를 입력하면 인증 매니저를 거쳐 Access와 Refresh 토큰 세트를 정상 발급해야 한다")
    fun loginSuccess() {
        // given
        val request = LoginCommand(email = "test@company.com", password = "validPassword123!")
        val unauthenticatedToken = UsernamePasswordAuthenticationToken(request.email, request.password)

        // 인증 성공 후 리턴될 유저 도메인 모델 생성
        val memberModel = MemberModel(
            id = 1L, email = request.email, password = "encrypted_password", userName = "홍길동", role = Role.ROLE_MEMBER
        )

        // 수정된 CustomUserDetails 스펙에 맞춰 isAdmin = false 주입
        val customUserDetails = CustomUserDetails(
            member = memberModel,
            groupIds = emptyList(),
            isAdmin = false
        )
        val authenticatedToken = UsernamePasswordAuthenticationToken(customUserDetails, null, customUserDetails.authorities)

        // Mocking 선언: 매니저가 인증 처리하면 성공 토큰을 뱉도록 조율
        `when`(authenticationManager.authenticate(unauthenticatedToken)).thenReturn(authenticatedToken)
        `when`(jwtTokenProvider.createAccessToken(authenticatedToken)).thenReturn("mocked-access-token")
        `when`(jwtTokenProvider.createRefreshToken(request.email)).thenReturn("mocked-refresh-token")

        // when
        val response = authService.login(request)

        // then
        assertThat(response.accessToken).isEqualTo("mocked-access-token")
        assertThat(response.refreshToken).isEqualTo("mocked-refresh-token")
    }

    @Test
    @DisplayName("인증 매니저 검증 단계에서 탈퇴한 회원이나 비밀번호 불일치로 예외가 터지면 서비스 레이어도 튕겨나가야 한다")
    fun loginFailInvalidCredentials() {
        // given
        val request = LoginCommand(email = "deleted@company.com", password = "anyPassword")
        val unauthenticatedToken = UsernamePasswordAuthenticationToken(request.email, request.password)

        // Mocking 선언: 매니저가 인증 처리할 때 탈퇴 가드 진영에서 BadCredentialsException을 터트린다고 가정
        `when`(authenticationManager.authenticate(unauthenticatedToken))
            .thenThrow(BadCredentialsException("탈퇴 처리된 계정입니다."))

        // when & then
        assertThatThrownBy { authService.login(request) }
            .isInstanceOf(BadCredentialsException::class.java)
            .hasMessageContaining("탈퇴 처리된 계정입니다.")
    }

    @Test
    @DisplayName("유효한 Refresh Token으로 재발급 시 새 토큰 세트를 정상 반환한다")
    fun reissueSuccess() {
        // given
        val refreshToken = "valid-refresh-token"
        val email = "test@company.com"
        val memberModel = MemberModel(
            id = 1L, email = email, password = "encrypted", userName = "홍길동", role = Role.ROLE_MEMBER
        )
        val customUserDetails = CustomUserDetails(member = memberModel, groupIds = emptyList(), isAdmin = false)
        val authentication = UsernamePasswordAuthenticationToken(customUserDetails, null, customUserDetails.authorities)

        `when`(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true)
        `when`(jwtTokenProvider.getAuthentication(refreshToken)).thenReturn(authentication)
        `when`(tokenRepository.getRefreshToken(email)).thenReturn(refreshToken) // 저장된 토큰과 일치
        `when`(jwtTokenProvider.createAccessToken(authentication)).thenReturn("new-access-token")
        `when`(jwtTokenProvider.createRefreshToken(email)).thenReturn("new-refresh-token")

        // when
        val response = authService.reissue(refreshToken)

        // then
        assertThat(response.accessToken).isEqualTo("new-access-token")
        assertThat(response.refreshToken).isEqualTo("new-refresh-token")
        verify(tokenRepository).saveRefreshToken(email, "new-refresh-token")
    }

    @Test
    @DisplayName("저장된 토큰과 불일치하면 탈취 의심으로 토큰과 캐시를 즉시 파기하고 예외를 던진다")
    fun reissueFailTokenMismatch() {
        // given
        val refreshToken = "stolen-refresh-token"
        val email = "test@company.com"
        val memberModel = MemberModel(
            id = 1L, email = email, password = "encrypted", userName = "홍길동", role = Role.ROLE_MEMBER
        )
        val customUserDetails = CustomUserDetails(member = memberModel, groupIds = emptyList(), isAdmin = false)
        val authentication = UsernamePasswordAuthenticationToken(customUserDetails, null, customUserDetails.authorities)

        `when`(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true)
        `when`(jwtTokenProvider.getAuthentication(refreshToken)).thenReturn(authentication)
        `when`(tokenRepository.getRefreshToken(email)).thenReturn("different-token") // 불일치

        // when & then
        assertThatThrownBy { authService.reissue(refreshToken) }
            .isInstanceOf(JwtException::class.java)

        // 핵심: 탈취 의심 시 강제 파기 검증
        verify(tokenRepository).deleteRefreshToken(email)
        verify(userCacheRepository).deleteUserCache(email)
    }

    @Test
    @DisplayName("유효하지 않은 Refresh Token이면 즉시 예외를 던진다")
    fun reissueFailInvalidToken() {
        // given
        val invalidToken = "invalid-token"
        `when`(jwtTokenProvider.validateToken(invalidToken)).thenReturn(false)

        // when & then
        assertThatThrownBy { authService.reissue(invalidToken) }
            .isInstanceOf(JwtException::class.java)
    }
}