package com.example.wiki_project.member.application.dto

import com.example.wiki_project.member.domain.model.MemberModel
import com.example.wiki_project.member.domain.model.Role
import java.time.LocalDateTime

/**
 * 회원 API 응답 DTO (비밀번호 해시 등 민감 정보 제외)
 */
data class MemberResponse(
    val id: Long?,
    val email: String,
    val userName: String?,
    val role: Role,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(model: MemberModel) = MemberResponse(
            id = model.id,
            email = model.email,
            userName = model.userName,
            role = model.role,
            createdAt = model.createdAt
        )
    }
}
