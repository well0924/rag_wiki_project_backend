package com.example.wiki_project.member.application.dto

import com.example.wiki_project.member.domain.model.GroupRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

// OWNER가 이메일로 멤버를 추가할 때의 요청
data class AddGroupMemberRequest(
    @field:NotBlank(message = "이메일은 필수입니다.")
    @field:Email(message = "이메일 형식이 올바르지 않습니다.")
    val email: String
)

// 그룹 멤버 목록 응답 (비밀번호 등 회원 엔티티 전체를 노출하지 않도록 필요한 값만 담음)
data class GroupMemberResponse(
    val memberId: Long,
    val email: String,
    val userName: String?,
    val role: GroupRole
)
