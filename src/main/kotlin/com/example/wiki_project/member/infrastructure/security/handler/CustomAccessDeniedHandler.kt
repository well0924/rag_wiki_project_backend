package com.example.wiki_project.member.infrastructure.security.handler

import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.common.exception.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class CustomAccessDeniedHandler(
    private val objectMapper: ObjectMapper
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        accessDeniedException: AccessDeniedException?
    ) {
        response?.status = HttpServletResponse.SC_FORBIDDEN
        response?.contentType = MediaType.APPLICATION_JSON_VALUE
        response?.characterEncoding = "UTF-8"

        val errorResponse = ErrorResponse(
            status = HttpServletResponse.SC_FORBIDDEN,
            error = "FORBIDDEN",
            message = "해당 자원에 접근할 권한이 없습니다."
        )
        response?.writer?.write(objectMapper.writeValueAsString(errorResponse))
    }

}