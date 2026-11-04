package com.example.wiki_project.domain.member

import com.example.wiki_project.member.application.MemberQueryService
import com.example.wiki_project.member.domain.entity.MemberEntity
import com.example.wiki_project.member.domain.entity.UserGroup
import com.example.wiki_project.member.domain.entity.UserGroupMember
import com.example.wiki_project.member.domain.model.MemberModel
import com.example.wiki_project.member.domain.model.Role
import com.example.wiki_project.member.infrastructure.UserGroupMemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class MemberQueryServiceTest {

    @Mock private lateinit var userGroupMemberRepository: UserGroupMemberRepository

    @InjectMocks
    private lateinit var memberQueryService: MemberQueryService

    @Test
    @DisplayName("관리자도 소속 그룹 ID를 조회하며, isAdmin은 관리자 기능 판단용으로만 true다")
    fun adminGetsOwnGroupIds() {
        val admin = MemberModel(id = 5L, email = "admin@company.com", password = "hashed", userName = "관리자", role = Role.ROLE_ADMIN)
        val adminEntity = MemberEntity(id = 5L, email = "admin@company.com", password = "hashed", role = "ROLE_ADMIN")
        whenever(userGroupMemberRepository.findByMemberId(5L)).doReturn(
            listOf(UserGroupMember(member = adminEntity, group = UserGroup(id = 7L, groupName = "운영팀")))
        )

        val result = memberQueryService.findGroupIds(admin)

        assertThat(result.groupIds).containsExactly(7L)
        assertThat(result.isAdmin).isTrue()
    }

    @Test
    @DisplayName("일반 회원은 소속 그룹 ID를 조회하고 isAdmin은 false다")
    fun memberGetsOwnGroupIds() {
        val member = MemberModel(id = 9L, email = "user@company.com", password = "hashed", userName = "회원")
        val memberEntity = MemberEntity(id = 9L, email = "user@company.com", password = "hashed", role = "ROLE_MEMBER")
        whenever(userGroupMemberRepository.findByMemberId(9L)).doReturn(
            listOf(
                UserGroupMember(member = memberEntity, group = UserGroup(id = 1L, groupName = "개발팀")),
                UserGroupMember(member = memberEntity, group = UserGroup(id = 2L, groupName = "CS 스터디"))
            )
        )

        val result = memberQueryService.findGroupIds(member)

        assertThat(result.groupIds).containsExactly(1L, 2L)
        assertThat(result.isAdmin).isFalse()
    }
}
