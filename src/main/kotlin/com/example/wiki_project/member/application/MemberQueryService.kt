package com.example.wiki_project.member.application

import com.example.wiki_project.member.application.dto.MemberAuthInfo
import com.example.wiki_project.member.domain.mapper.toModel
import com.example.wiki_project.member.domain.model.MemberModel
import com.example.wiki_project.member.infrastructure.MemberRepository
import com.example.wiki_project.member.infrastructure.UserGroupMemberRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import com.example.wiki_project.member.domain.model.Role;

@Component
@Transactional(readOnly = true)
class MemberQueryService(
    private val userGroupMemberRepository: UserGroupMemberRepository
) {

    /**
     * 이메일 기반 유저 그룹 ID 리스트 조회
     * ROLE_ADMIN 권한을 가진 유저는 전사 문서 접근 권한이 있으므로
     * 매핑 테이블(user_group_members)을 조회하지 않고 빈 리스트를 반환 (불필요한 쿼리 생략).
     */
    fun findGroupIds(memberModel: MemberModel): MemberAuthInfo {
        if (memberModel.role == Role.ROLE_ADMIN) {
            return MemberAuthInfo(emptyList(), true)
        }
        val mappings = userGroupMemberRepository.findByMemberId(memberModel.id!!)
        return MemberAuthInfo(mappings.map { it.group.id!! }, false)
    }
}