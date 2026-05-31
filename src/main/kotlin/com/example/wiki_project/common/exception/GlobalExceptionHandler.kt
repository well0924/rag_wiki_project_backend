package com.example.wiki_project.common.exception

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    // 우리가 정의한 비즈니스 예외 처리
    @ExceptionHandler(BusinessException::class)
    protected fun handleBusinessException(e: BusinessException): ResponseEntity<ErrorResponse> {
        val errorCode = e.errorCode
        val response = ErrorResponse.of(errorCode)
        return ResponseEntity(response, errorCode.status)
    }

    // 그 외 예상치 못한 런타임 예외 처리
    @ExceptionHandler(Exception::class)
    protected fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR)
        return ResponseEntity(response, ErrorCode.INTERNAL_SERVER_ERROR.status)
    }
}