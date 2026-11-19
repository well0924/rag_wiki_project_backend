package com.example.wiki_project.member.infrastructure

import com.example.wiki_project.member.domain.entity.UserGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserGroupRepository: JpaRepository<UserGroup, Long>
// 그룹 이름 전체 조회(findByGroupName)는 제거: 이름은 OWNER 기준으로만 중복 검사 (UserGroupMemberRepository)