package com.example.wiki_project.member.ui

import com.example.wiki_project.member.application.AuthService
import com.example.wiki_project.member.application.dto.LoginCommand
import com.example.wiki_project.member.application.dto.TokenResponse
import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetails
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class AuthController(
    private val authService: AuthService
) {

    @Operation(summary = "로그인", description = "이메일과 패스워드로 로그인하여 Access/Refresh Token을 발급받습니다.")
    @PostMapping("/auth/login")
    fun login(@RequestBody command: LoginCommand): ResponseEntity<TokenResponse> {
        val tokenResponse = authService.login(command)
        return ResponseEntity.ok(tokenResponse)
    }

    @Operation(summary = "토큰 재발급", description = "만료된 Access Token 대신 Refresh Token을 활용해 이중 토큰 세트를 재발급합니다.")
    @PostMapping("/auth/reissue")
    fun reissue(
        @Parameter(description = "X-Refresh-Token 헤더에 리프레시 토큰 주입")
        @RequestHeader("X-Refresh-Token") refreshToken: String
    ): ResponseEntity<TokenResponse> {
        val tokenResponse = authService.reissue(refreshToken)
        return ResponseEntity.ok(tokenResponse)
    }

    @Operation(summary = "로그아웃", description = "현재 사용 중인 토큰을 만료시키고 세션을 파기합니다.", security = [SecurityRequirement(name = "BearerAuth")])
    @PostMapping("/members/logout")
    fun logout(
        request: HttpServletRequest,
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ResponseEntity<Unit> {
        val authHeader = request.getHeader(HttpHeaders.AUTHORIZATION)
            ?: throw IllegalArgumentException("인증 헤더가 누락되었습니다.")
        val accessToken = authHeader.substring(7)
        val email = userDetails.username

        authService.logout(accessToken, email)
        return ResponseEntity.noContent().build()
    }
}