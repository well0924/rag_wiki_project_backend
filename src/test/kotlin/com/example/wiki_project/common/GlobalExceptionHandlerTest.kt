package com.example.wiki_project.common

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.common.exception.GlobalExceptionHandler
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

class GlobalExceptionHandlerTest {

    data class SampleRequest(
        @field:NotBlank(message = "질문 내용은 공백일 수 없습니다.")
        val query: String
    )

    @RestController
    class SampleController {
        @PostMapping("/sample/valid")
        fun valid(@Valid @RequestBody request: SampleRequest) = request.query

        @GetMapping("/sample/require")
        fun require(): String {
            require(false) { "유저 이름은 공백일 수 없습니다." }
            return "unreachable"
        }

        @GetMapping("/sample/business")
        fun business(): String = throw BusinessException(ErrorCode.UNSUPPORTED_DOCUMENT_TYPE)

        @GetMapping("/sample/unexpected")
        fun unexpected(): String = throw RuntimeException("내부 구현 상세 메시지")
    }

    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(SampleController())
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @Test
    @DisplayName("@Valid 검증 실패는 500이 아닌 400과 필드 메시지로 응답한다")
    fun validationFailureIs400() {
        mockMvc.perform(post("/sample/valid").contentType(MediaType.APPLICATION_JSON).content("""{"query":"  "}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("질문 내용은 공백일 수 없습니다."))
    }

    @Test
    @DisplayName("JSON 본문 형식 오류는 400으로 응답한다")
    fun malformedJsonIs400() {
        mockMvc.perform(post("/sample/valid").contentType(MediaType.APPLICATION_JSON).content("""{"query":"""))
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("도메인 require() 위반은 400과 규칙 메시지로 응답한다")
    fun requireViolationIs400() {
        mockMvc.perform(get("/sample/require"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("유저 이름은 공백일 수 없습니다."))
    }

    @Test
    @DisplayName("비즈니스 예외는 ErrorCode에 정의된 상태 코드로 응답한다 (지원하지 않는 형식 → 400)")
    fun businessExceptionUsesErrorCodeStatus() {
        mockMvc.perform(get("/sample/business"))
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("예상치 못한 예외는 내부 메시지를 노출하지 않고 500으로 응답한다")
    fun unexpectedExceptionHidesDetails() {
        mockMvc.perform(get("/sample/unexpected"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.message))
    }
}
