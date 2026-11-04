package com.example.wiki_project.member.ui

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.member.application.GroupService
import com.example.wiki_project.member.application.dto.AddGroupMemberRequest
import com.example.wiki_project.member.application.dto.CreateGroupRequest
import com.example.wiki_project.member.application.dto.GroupMemberResponse
import com.example.wiki_project.member.application.dto.UpdateGroupRequest
import com.example.wiki_project.member.domain.model.UserGroupModel
import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetails
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
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

    @Operation(summary = "새 그룹 생성", description = "로그인한 사용자가 새 그룹(워크스페이스)을 만들고 OWNER가 됩니다.", security = [SecurityRequirement(name = "BearerAuth")])
    @PostMapping
    fun createGroup(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @RequestBody request: CreateGroupRequest
    ): ResponseEntity<UserGroupModel> {
        // 생성자는 요청 본문이 아닌 인증 정보의 회원 ID 사용
        val model = groupService.createGroup(userDetails.requesterId(), request.groupName, request.description)
        return ResponseEntity.status(HttpStatus.CREATED).body(model)
    }

    @Operation(summary = "내 소속 그룹 목록 조회", description = "현재 로그인한 유저가 속한 모든 그룹 목록을 가져옵니다. (사이드바 연동용)", security = [SecurityRequirement(name = "BearerAuth")])
    @GetMapping("/me")
    fun getMyGroups(
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ResponseEntity<List<UserGroupModel>> {
        return ResponseEntity.ok(groupService.getMyGroups(userDetails.requesterId()))
    }

    @Operation(summary = "그룹 정보 수정 (OWNER)", description = "그룹의 이름이나 설명을 수정합니다. 그룹 OWNER만 가능합니다.", security = [SecurityRequirement(name = "BearerAuth")])
    @PutMapping("/{groupId}")
    fun updateGroup(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PathVariable groupId: Long,
        @RequestBody request: UpdateGroupRequest
    ): ResponseEntity<UserGroupModel> {
        val model = groupService.updateGroup(groupId, userDetails.requesterId(), request.groupName, request.description)
        return ResponseEntity.ok(model)
    }

    @Operation(summary = "그룹 삭제 (OWNER)", description = "그룹과 멤버 매핑을 삭제합니다. 그룹 OWNER만 가능합니다.", security = [SecurityRequirement(name = "BearerAuth")])
    @DeleteMapping("/{groupId}")
    fun deleteGroup(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PathVariable groupId: Long
    ): ResponseEntity<Void> {
        groupService.deleteGroup(groupId, userDetails.requesterId())
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "그룹 멤버 목록 조회", description = "그룹에 속한 회원과 역할(OWNER / MEMBER)을 조회합니다. 그룹 소속 회원만 가능합니다.", security = [SecurityRequirement(name = "BearerAuth")])
    @GetMapping("/{groupId}/members")
    fun getGroupMembers(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PathVariable groupId: Long
    ): ResponseEntity<List<GroupMemberResponse>> {
        return ResponseEntity.ok(groupService.getGroupMembers(groupId, userDetails.requesterId()))
    }

    @Operation(summary = "이메일로 멤버 추가 (OWNER)", description = "가입된 회원을 이메일로 찾아 MEMBER로 바로 추가합니다. 초대 수락 단계는 없습니다.", security = [SecurityRequirement(name = "BearerAuth")])
    @PostMapping("/{groupId}/members")
    fun addMember(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PathVariable groupId: Long,
        @Valid @RequestBody request: AddGroupMemberRequest
    ): ResponseEntity<GroupMemberResponse> {
        val added = groupService.addMemberByEmail(groupId, userDetails.requesterId(), request.email)
        return ResponseEntity.status(HttpStatus.CREATED).body(added)
    }

    @Operation(summary = "멤버 제거 (OWNER)", description = "그룹에서 회원을 제거합니다. OWNER 자신은 제거할 수 없습니다.", security = [SecurityRequirement(name = "BearerAuth")])
    @DeleteMapping("/{groupId}/members/{memberId}")
    fun removeMember(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PathVariable groupId: Long,
        @PathVariable memberId: Long
    ): ResponseEntity<Void> {
        groupService.removeMember(groupId, userDetails.requesterId(), memberId)
        return ResponseEntity.noContent().build()
    }

    private fun CustomUserDetails.requesterId(): Long =
        member.id ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)
}
