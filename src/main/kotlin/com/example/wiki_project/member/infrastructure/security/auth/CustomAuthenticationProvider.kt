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

        // 캐시 또는 DB에서 회원 정보 조회
        val userDetails = try {
            userDetailsService.loadUserByUsername(email.toString()) as? CustomUserDetails
        } catch (e: Exception) {
            e.printStackTrace()
            throw BadCredentialsException("인증 정보 조회 중 에러가 발생했습니다.", e)
        }

        if (userDetails == null) {
            throw BadCredentialsException("존재하지 않는 회원입니다.")
        }
        val member = userDetails.member

        // 탈퇴(Soft Delete)한 회원은 로그인 차단
        if (member.isDeleted) {
            throw BadCredentialsException("탈퇴 처리된 계정입니다.")
        }

        // 비밀번호 매칭 검증
        if (!passwordEncoder.matches(password, member.password)) {
            throw BadCredentialsException("비밀번호가 일치하지 않습니다.")
        }

        // 인증 성공: 권한 정보를 담은 인증 완료 토큰 반환
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