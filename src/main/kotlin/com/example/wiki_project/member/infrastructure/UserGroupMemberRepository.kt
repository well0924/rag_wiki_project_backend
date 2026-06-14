package com.example.wiki_project.member.infrastructure

import com.example.wiki_project.member.domain.entity.UserGroup
import com.example.wiki_project.member.domain.entity.UserGroupMember
import com.example.wiki_project.member.domain.entity.UserGroupMemberId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserGroupMemberRepository: JpaRepository<UserGroupMember, UserGroupMemberId> {
    // 특정 유저가 속한 그룹 매핑 데이터를 싹 긁어오기 위한 쿼리 메서드
    fun findByMemberId(memberId: Long): List<UserGroupMember>

    fun deleteByGroup(group: UserGroup)
}