package com.example.wiki_project.member.infrastructure

import com.example.wiki_project.member.domain.entity.UserGroup
import com.example.wiki_project.member.domain.entity.UserGroupMember
import com.example.wiki_project.member.domain.entity.UserGroupMemberId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface UserGroupMemberRepository: JpaRepository<UserGroupMember, UserGroupMemberId> {
    // 회원이 속한 그룹 매핑 전체 조회
    fun findByMemberId(memberId: Long): List<UserGroupMember>

    fun findByGroup(group: UserGroup): List<UserGroupMember>

    // 그룹 멤버 목록 조회 (회원 정보를 함께 가져와 멤버 수만큼 추가 조회가 나가지 않게 함)
    @Query("select ugm from UserGroupMember ugm join fetch ugm.member where ugm.group.id = :groupId")
    fun findWithMemberByGroupId(groupId: Long): List<UserGroupMember>

    fun deleteByGroup(group: UserGroup)
}
