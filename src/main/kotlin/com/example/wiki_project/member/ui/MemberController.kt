package com.example.wiki_project.member.ui

import com.example.wiki_project.member.application.MemberService
import com.example.wiki_project.member.application.dto.*
import com.example.wiki_project.member.domain.model.MemberModel
import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetails
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class MemberController(
    private val memberService: MemberService
) {
    @Operation(summary = "회원 가입", description = "새로운 회원을 등록합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "회원가입 성공"),
        ApiResponse(responseCode = "400", description = "이미 존재하는 이메일 등 중복 오류")
    )
    @PostMapping("/auth/register")
    fun register(@RequestBody command: UserCreateCommand): ResponseEntity<MemberModel> {
        val result = memberService.registerMember(command)
        return ResponseEntity.ok(result)
    }

    @Operation(summary = "회원 단건 조회", description = "회원의 고유 식별자(ID)로 상세 정보를 조회합니다.")
    @GetMapping("/members/{id}")
    fun findById(@PathVariable("id") id: Long): ResponseEntity<MemberModel> {
        val memberResponse = memberService.getMemberById(id)
        return ResponseEntity.ok(memberResponse)
    }

    @Operation(summary = "회원 단건 조회", description = "회원의 고유 이메일로 상세 정보를 조회합니다.")
    @GetMapping("/members")
    fun findByEmail(@RequestParam("email") email: String): ResponseEntity<MemberModel> {
        val memberResponse = memberService.getMemberByEmail(email)
        return ResponseEntity.ok(memberResponse)
    }

    @Operation(summary = "회원 프로필 통합 수정", description = "현재 로그인한 사용자의 프로필 정보(이름 등)를 수정합니다.", security = [SecurityRequirement(name = "BearerAuth")])
    @PutMapping("/members/me")
    fun updateProfile(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @RequestBody command: UserUpdateCommand
    ): ResponseEntity<MemberModel> {
        val memberId = userDetails.member.id!!
        val updatedMember = memberService.updateMember(memberId, command)
        return ResponseEntity.ok(updatedMember)
    }

    @Operation(summary = "회원 이름 단독 변경", description = "회원의 이름을 전용 비즈니스 로직에 맞춰 변경합니다.", security = [SecurityRequirement(name = "BearerAuth")])
    @PatchMapping("/members/me/username")
    fun updateUserName(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @RequestParam("newName") newName: String
    ): ResponseEntity<MemberModel> {
        val memberId = userDetails.member.id!!
        val updatedMember = memberService.updateUserName(memberId, newName)
        return ResponseEntity.ok(updatedMember)
    }

    @Operation(summary = "회원 탈퇴 (삭제)", description = "현재 로그인한 사용자의 계정을 Soft Delete 처리하고 세션을 추방합니다.", security = [SecurityRequirement(name = "BearerAuth")])
    @DeleteMapping("/members/me")
    fun withdraw(
        request: HttpServletRequest,
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ResponseEntity<Unit> {
        val memberId = userDetails.member.id!!
        val authHeader = request.getHeader(HttpHeaders.AUTHORIZATION)
            ?: throw IllegalArgumentException("인증 헤더가 누락되었습니다.")
        val accessToken = authHeader.substring(7)

        memberService.deleteMember(memberId, accessToken)
        return ResponseEntity.noContent().build()
    }
}