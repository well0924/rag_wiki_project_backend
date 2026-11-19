package com.example.wiki_project.member.infrastructure

import com.example.wiki_project.member.domain.entity.UserGroup
import com.example.wiki_project.member.domain.entity.UserGroupMember
import com.example.wiki_project.member.domain.entity.UserGroupMemberId
import com.example.wiki_project.member.domain.model.GroupRole
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

    // 회원이 지정한 역할(OWNER)로 속한 그룹 중 같은 이름의 그룹 수 (excludeGroupId 그룹은 제외)
    // - 그룹 이름은 서비스 전체가 아니라 "만든 사람(OWNER) 기준"으로만 중복을 막음
    // - OWNER 정보가 매핑 테이블에 있어 DB 복합 유니크 제약 대신 조회로 검사
    @Query(
        """
        select count(ugm) from UserGroupMember ugm
        where ugm.member.id = :memberId
          and ugm.role = :role
          and ugm.group.groupName = :groupName
          and ugm.group.id <> :excludeGroupId
        """
    )
    fun countGroupsByMemberRoleAndName(memberId: Long, role: GroupRole, groupName: String, excludeGroupId: Long): Long
}
