package com.example.wiki_project.member.infrastructure.security.handler

import com.example.wiki_project.common.exception.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class CustomAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        authException: AuthenticationException?
    ) {
        response?.status = HttpServletResponse.SC_UNAUTHORIZED
        response?.contentType = MediaType.APPLICATION_JSON_VALUE
        response?.characterEncoding = "UTF-8"

        val errorResponse = ErrorResponse(
            status = HttpServletResponse.SC_UNAUTHORIZED,
            error = "UNAUTHORIZED",
            message = "인증이 필요합니다."
        )
        response?.writer?.write(objectMapper.writeValueAsString(errorResponse))
    }
}