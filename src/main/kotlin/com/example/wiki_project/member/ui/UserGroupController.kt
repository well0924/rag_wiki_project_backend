package com.example.wiki_project.member.ui

import com.example.wiki_project.member.application.GroupService
import com.example.wiki_project.member.application.dto.CreateGroupRequest
import com.example.wiki_project.member.application.dto.UpdateGroupRequest
import com.example.wiki_project.member.domain.model.UserGroupModel
import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetails
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/groups")
class UserGroupController(
    private val groupService: GroupService
) {

    @Operation(summary = "새 권한 그룹 생성", description = "로그인한 사용자가 새로운 프로젝트/커스텀 위키 그룹을 생성합니다.", security = [SecurityRequirement(name = "BearerAuth")])
    @PostMapping
    fun createGroup(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @RequestBody request: CreateGroupRequest
    ): ResponseEntity<UserGroupModel> {
        // 생성자는 요청 본문이 아닌 인증 정보의 회원 ID 사용
        val model = userDetails.member.id?.let { groupService.createGroup(it, request.groupName, request.description) }
        return ResponseEntity.status(HttpStatus.CREATED).body(model)
    }

    @Operation(summary = "내 소속 그룹 목록 조회", description = "현재 로그인한 유저가 속한 모든 권한 그룹 목록을 가져옵니다. (사이드바 연동용)", security = [SecurityRequirement(name = "BearerAuth")])
    @GetMapping("/me")
    fun getMyGroups(
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ResponseEntity<List<UserGroupModel>> {
        val models = userDetails.member.id?.let { groupService.getMyGroups(it) }
        return ResponseEntity.ok(models)
    }

    @Operation(summary = "그룹 정보 수정", description = "그룹의 이름이나 설명을 수정합니다. (도메인 유효성 검증 포함)", security = [SecurityRequirement(name = "BearerAuth")])
    @PutMapping("/{groupId}")
    fun updateGroup(
        @PathVariable groupId: Long,
        @RequestBody request: UpdateGroupRequest
    ): ResponseEntity<UserGroupModel> {
        val model = groupService.updateGroup(groupId, request.groupName, request.description)
        return ResponseEntity.ok(model)
    }

    @Operation(summary = "그룹 삭제", description = "특정 그룹을 파기하고 연관된 매핑 관계를 해제합니다.", security = [SecurityRequirement(name = "BearerAuth")])
    @DeleteMapping("/{groupId}")
    fun deleteGroup(
        @PathVariable groupId: Long
    ): ResponseEntity<Void> {
        groupService.deleteGroup(groupId)
        return ResponseEntity.noContent().build()
    }
}