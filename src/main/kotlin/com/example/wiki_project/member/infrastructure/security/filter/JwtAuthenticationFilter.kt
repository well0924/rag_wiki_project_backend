package com.example.wiki_project.member.infrastructure.security.filter

import com.example.wiki_project.member.infrastructure.security.jwt.JwtTokenProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val redisTemplate: StringRedisTemplate // 로그아웃/블랙리스트 조회용 레디스 주입
): OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = resolveToken(request)
        print(token)
        // 토큰이 없는 경우
        if (token == null) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            // 토큰이 유효한지 1차 검증
            if (token != null && jwtTokenProvider.validateToken(token)) {
                // [Blacklist Pre-check] 해당 토큰이 블랙리스트에 등록되어 있는지 확인
                if (redisTemplate.hasKey("blacklist:$token")==true) {
                    // 시큐리티 코어 예외 던지기 -> 상위 JwtExceptionFilter가 잡아서 401 JSON 응답 스나이핑
                    throw BadCredentialsException("로그아웃되거나 파기된 인증 자격 증명입니다.")
                }

                // 블랙리스트 통과 시 시큐리티 텍스트에 인증 객체 장착
                val authentication = jwtTokenProvider.getAuthentication(token)
                SecurityContextHolder.getContext().authentication = authentication
            }
        }catch(e:Exception) {
            throw e
        }

        filterChain.doFilter(request, response)
    }

    /**
     * 토큰 추출
     * 토큰 재발급시에 필요한 추출코드 추가.
     **/
    private fun resolveToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7)
        }

        val refreshToken = request.getHeader("X-Refresh-Token")
        if (StringUtils.hasText(refreshToken)) {
            return refreshToken // 리프레시 토큰은 Bearer 안 붙이고 순수 문자열로 받기로 했으니 그대로 리턴
        }
        return null
    }
}