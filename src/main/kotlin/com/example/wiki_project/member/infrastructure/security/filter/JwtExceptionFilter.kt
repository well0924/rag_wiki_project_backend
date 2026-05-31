package com.example.wiki_project.member.infrastructure.security.filter

import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.common.exception.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtExceptionFilter(
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            // 다음 필터(JwtAuthenticationFilter)로 넘김
            filterChain.doFilter(request, response)
        } catch (e: ExpiredJwtException) {
            setErrorResponse(response, ErrorCode.UNAUTHORIZED, "만료된 JWT 토큰입니다.")
        } catch (e: JwtException) {
            setErrorResponse(response, ErrorCode.UNAUTHORIZED, "유효하지 않은 JWT 토큰입니다.")
        } catch (e: BadCredentialsException) {
            setErrorResponse(response, ErrorCode.UNAUTHORIZED, e.message ?: "로그아웃된 토큰입니다.")
        } catch (e: Exception) {
            setErrorResponse(response, ErrorCode.INTERNAL_SERVER_ERROR, "인증 필터 내부 에러가 발생했습니다.")
        }
    }

    private fun setErrorResponse(response: HttpServletResponse, errorCode: ErrorCode, customMessage: String) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"

        val errorResponse = ErrorResponse(
            status = errorCode.status.value(),
            error = errorCode.status.name,
            message = customMessage
        )
        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}