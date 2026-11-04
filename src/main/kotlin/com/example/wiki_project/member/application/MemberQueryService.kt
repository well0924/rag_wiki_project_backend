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
     * 유저 그룹 ID 리스트 조회
     * 관리자도 일반 회원과 같이 소속 그룹의 문서만 볼 수 있으므로 역할과 관계없이 매핑 테이블을 조회.
     * isAdmin은 회원 관리 / 품질 모니터링 같은 관리자 기능의 접근 판단에만 사용.
     */
    fun findGroupIds(memberModel: MemberModel): MemberAuthInfo {
        val mappings = userGroupMemberRepository.findByMemberId(memberModel.id!!)
        return MemberAuthInfo(mappings.map { it.group.id!! }, memberModel.role == Role.ROLE_ADMIN)
    }
}