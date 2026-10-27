package com.example.wiki_project.common.exception

import io.jsonwebtoken.JwtException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.validation.BindException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    // 우리가 정의한 비즈니스 예외 처리
    @ExceptionHandler(BusinessException::class)
    protected fun handleBusinessException(e: BusinessException): ResponseEntity<ErrorResponse> {
        val errorCode = e.errorCode
        val response = ErrorResponse.of(errorCode)
        return ResponseEntity(response, errorCode.status)
    }

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(e: BadCredentialsException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(status = 401, error = "UNAUTHORIZED", message = e.message ?: "인증 실패"))
    }

    // 토큰 재발급 실패 (위조 / 만료 / 탈취 의심 Refresh Token)
    @ExceptionHandler(JwtException::class)
    fun handleJwtException(e: JwtException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(status = 401, error = "UNAUTHORIZED", message = e.message ?: "유효하지 않은 토큰입니다."))
    }

    // @Valid 검증 실패 (@RequestBody: MethodArgumentNotValidException, @ModelAttribute: BindException)
    @ExceptionHandler(BindException::class)
    fun handleBindException(e: BindException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
            ?: ErrorCode.INVALID_INPUT_VALUE.message
        return badRequest(message)
    }

    // 메서드 파라미터 단위 검증 실패 (Spring 6.1+)
    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleHandlerMethodValidation(e: HandlerMethodValidationException): ResponseEntity<ErrorResponse> {
        val message = e.allErrors.firstOrNull()?.defaultMessage ?: ErrorCode.INVALID_INPUT_VALUE.message
        return badRequest(message)
    }

    // 도메인 모델의 require() / check() 규칙 위반 → 클라이언트 입력 오류로 응답
    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
    fun handleIllegalArgument(e: RuntimeException): ResponseEntity<ErrorResponse> {
        return badRequest(e.message ?: ErrorCode.INVALID_INPUT_VALUE.message)
    }

    // JSON 본문 파싱 실패, 필수 파라미터/헤더/파트 누락, 타입 불일치
    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MissingServletRequestParameterException::class,
        MissingRequestHeaderException::class,
        MissingServletRequestPartException::class,
        MethodArgumentTypeMismatchException::class
    )
    fun handleMalformedRequest(e: Exception): ResponseEntity<ErrorResponse> {
        return badRequest(ErrorCode.INVALID_INPUT_VALUE.message)
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSize(e: MaxUploadSizeExceededException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(ErrorResponse(status = 413, error = "PAYLOAD_TOO_LARGE", message = "업로드 가능한 파일 크기를 초과했습니다."))
    }

    // 존재하지 않는 URL (기존에는 하단 Exception 핸들러에 잡혀 500으로 응답되던 문제)
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(e: NoResourceFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(status = 404, error = "NOT_FOUND", message = "요청한 경로를 찾을 수 없습니다."))
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(e: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ErrorResponse(status = 405, error = "METHOD_NOT_ALLOWED", message = "지원하지 않는 HTTP 메서드입니다."))
    }

    // 그 외 예상치 못한 런타임 예외 처리 (원인 추적을 위해 스택트레이스 로깅)
    @ExceptionHandler(Exception::class)
    protected fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("처리되지 않은 예외 발생", e)
        val response = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR)
        return ResponseEntity(response, ErrorCode.INTERNAL_SERVER_ERROR.status)
    }

    private fun badRequest(message: String): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(status = 400, error = "BAD_REQUEST", message = message))
    }
}
