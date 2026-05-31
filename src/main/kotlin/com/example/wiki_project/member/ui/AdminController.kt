package com.example.wiki_project.member.ui

import com.example.wiki_project.member.application.AdminService
import com.example.wiki_project.member.application.dto.UserGroupAssignCommand
import com.example.wiki_project.member.domain.model.MemberModel
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminController(
    private val adminService: AdminService
) {
    @Operation(summary = "관리자 권한 승격", description = "특정 회원을 ADMIN 권한으로 승격시킵니다. (백오피스 전용)", security = [SecurityRequirement(name = "BearerAuth")])
    @PatchMapping("/members/{id}/promote")
    fun promoteToAdmin(@PathVariable("id") id: Long): ResponseEntity<MemberModel> {
        val updatedMember = adminService.promoteToAdmin(id)
        return ResponseEntity.ok(updatedMember)
    }

    @Operation(summary = "회원 그룹 지정", description = "회원을 기밀 문서를 공유할 특정 조직/그룹에 매핑합니다. (백오피스 전용)", security = [SecurityRequirement(name = "BearerAuth")])
    @PostMapping("/members/assign-group")
    fun assignGroup(@RequestBody command: UserGroupAssignCommand): ResponseEntity<Unit> {
        adminService.assignGroup(command)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "회원 강제 추방 (탈퇴)", description = "어드민 권한으로 특정 분탕 유저를 강제 Soft Delete 처리하고 세션을 추방합니다.", security = [SecurityRequirement(name = "BearerAuth")])
    @DeleteMapping("/members/{id}")
    fun forceWithdraw(@PathVariable("id") id: Long): ResponseEntity<Unit> {
        // 서비스 단에 새로 추가할 어드민 전용 강제 탈퇴 메서드 호출
        adminService.forceWithdrawMemberByAdmin(id)
        return ResponseEntity.noContent().build()
    }
}