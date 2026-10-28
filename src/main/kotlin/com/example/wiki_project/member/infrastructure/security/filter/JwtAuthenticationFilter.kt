package com.example.wiki_project.member.infrastructure.security.filter

import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetailsService
import com.example.wiki_project.member.infrastructure.security.jwt.JwtTokenProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val userDetailsService: CustomUserDetailsService, // Redis 캐시 우선 → 미스 시 DB 조회
    private val redisTemplate: StringRedisTemplate // 로그아웃/블랙리스트 조회용 레디스 주입
): OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = resolveToken(request)

        // Access Token만 인증에 사용 (Refresh Token은 /auth/reissue 에서만 직접 검증)
        if (token != null && jwtTokenProvider.validateToken(token) && jwtTokenProvider.isAccessToken(token)) {
            // [Blacklist Pre-check] 해당 토큰이 블랙리스트에 등록되어 있는지 확인
            if (redisTemplate.hasKey("blacklist:$token") == true) {
                // 예외를 던지면 앞단의 JwtExceptionFilter 가 401 JSON 응답으로 변환
                throw BadCredentialsException("로그아웃되거나 파기된 인증 자격 증명입니다.")
            }

            // 토큰 클레임 대신 캐시/DB의 최신 회원 정보(id, role, groupIds)로 인증 객체 구성
            val userDetails = try {
                userDetailsService.loadUserByUsername(jwtTokenProvider.getEmail(token))
            } catch (e: UsernameNotFoundException) {
                throw BadCredentialsException("존재하지 않는 회원입니다.")
            }
            if (!userDetails.isEnabled) {
                throw BadCredentialsException("탈퇴 처리된 계정입니다.")
            }

            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userDetails, token, userDetails.authorities)
        }

        filterChain.doFilter(request, response)
    }

    /**
     * Authorization 헤더의 Bearer 토큰 추출
     **/
    private fun resolveToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7)
        }
        return null
    }
}
