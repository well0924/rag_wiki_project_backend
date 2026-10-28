package com.example.wiki_project.member.infrastructure

import com.example.wiki_project.member.domain.entity.UserGroup
import com.example.wiki_project.member.domain.entity.UserGroupMember
import com.example.wiki_project.member.domain.entity.UserGroupMemberId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserGroupMemberRepository: JpaRepository<UserGroupMember, UserGroupMemberId> {
    // 회원이 속한 그룹 매핑 전체 조회
    fun findByMemberId(memberId: Long): List<UserGroupMember>

    fun findByGroup(group: UserGroup): List<UserGroupMember>

    fun deleteByGroup(group: UserGroup)
}