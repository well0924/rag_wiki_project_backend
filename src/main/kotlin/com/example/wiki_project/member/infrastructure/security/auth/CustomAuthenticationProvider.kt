package com.example.wiki_project.member.infrastructure.security.auth

import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class CustomAuthenticationProvider(
    private val userDetailsService: CustomUserDetailsService,
    private val passwordEncoder: PasswordEncoder
): AuthenticationProvider {

    override fun authenticate(authentication: Authentication?): Authentication {
        val email = authentication?.name
        val password = authentication?.credentials.toString()

        // DB 또는 캐시에서 유저 정보 긁어오기
        val userDetails = try {
            userDetailsService.loadUserByUsername(email.toString()) as? CustomUserDetails
        } catch (e: Exception) {
            e.printStackTrace() // 콘솔에 에러의 본질(NPE, JPA 에러 등)을 빨갛게 출력해줌
            throw BadCredentialsException("인증 정보 조회 중 에러가 발생했습니다.", e)
        }

        if (userDetails == null) {
            throw BadCredentialsException("존재하지 않는 회원입니다.")
        }
        val member = userDetails.member

        // Soft Delete된 유저(탈퇴 회원) 로그인 시도 원천 차단
        if (member.isDeleted) {
            throw BadCredentialsException("탈퇴 처리된 계정입니다.")
            // Security 내부 에러 숨김 원칙을 위해 BadCredentials을 쓰거나,
            // 구체적인 커스텀 예외를 터트려 전역 필터에서 잡게 해도 됨
        }

        // 비밀번호 매칭 검증
        if (!passwordEncoder.matches(password, member.password)) {
            throw BadCredentialsException("비밀번호가 일치하지 않습니다.")
        }

        // 인증 성공 시, 권한(Role)을 채운 마킹된 토큰 발행
        return UsernamePasswordAuthenticationToken(
            userDetails,
            null,
            userDetails.authorities
        )
    }

    // 이 프로바이더가 어떤 형식의 인증 토큰을 지원할지 명시 (일반 폼 로그인 스펙 지원)
    override fun supports(authentication: Class<*>?): Boolean {
        return UsernamePasswordAuthenticationToken::class.java.isAssignableFrom(authentication)
    }

}