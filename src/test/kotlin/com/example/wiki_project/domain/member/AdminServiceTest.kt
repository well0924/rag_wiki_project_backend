package com.example.wiki_project.domain.member

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.member.application.AdminService
import com.example.wiki_project.member.application.dto.UserGroupAssignCommand
import com.example.wiki_project.member.domain.entity.MemberEntity
import com.example.wiki_project.member.domain.entity.UserGroup
import com.example.wiki_project.member.domain.entity.UserGroupMemberId
import com.example.wiki_project.member.domain.model.Role
import com.example.wiki_project.member.infrastructure.MemberRepository
import com.example.wiki_project.member.infrastructure.UserGroupMemberRepository
import com.example.wiki_project.member.infrastructure.UserGroupRepository
import com.example.wiki_project.member.infrastructure.security.RedisTokenRepository
import com.example.wiki_project.member.infrastructure.security.UserCacheRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.any
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class AdminServiceTest {

    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var userGroupRepository: UserGroupRepository
    @Mock private lateinit var userGroupMemberRepository: UserGroupMemberRepository
    @Mock private lateinit var tokenRepository: RedisTokenRepository
    @Mock private lateinit var userCacheRepository: UserCacheRepository

    @InjectMocks
    private lateinit var adminService: AdminService

    @Nested
    @DisplayName("관리자 권한 승격 테스트")
    inner class PromoteToAdminTest {

        @Test
        @DisplayName("일반 회원을 성공적으로 관리자(ROLE_ADMIN) 권한으로 승격하고 캐시를 파기한다")
        fun promoteSuccess() {
            // given
            val memberId = 1L
            val memberEntity = MemberEntity(id = memberId, email = "user@company.com", password = "hashed", userName = "유저", role = Role.ROLE_MEMBER.toString())

            `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(memberEntity))
            `when`(memberRepository.save(any())).thenAnswer { it.arguments[0] }

            // when
            val result = adminService.promoteToAdmin(memberId)

            // then
            assertThat(result.role).isEqualTo(Role.ROLE_ADMIN)
            verify(memberRepository).save(any())
            verify(userCacheRepository).deleteUserCache("user@company.com") // 실시간 보안 가드 작동 증명
            verify(tokenRepository).deleteRefreshToken("user@company.com") // 추가
        }
    }

    @Nested
    @DisplayName("그룹 강제 지정 테스트")
    inner class AssignGroupTest {

        @Test
        @DisplayName("이미 동일한 그룹에 가입된 회원이면 ALREADY_JOINED_GROUP 예외를 뱉어낸다")
        fun assignGroupFailWhenAlreadyJoined() {
            // given
            val command = UserGroupAssignCommand(memberId = 1L, groupId = 100L)
            val memberEntity = MemberEntity(id = 1L, email = "user@company.com", password = "pwd", userName = "유저", role = Role.ROLE_MEMBER.toString())
            val groupEntity = UserGroup(id = 100L, groupName = "인프라팀", description = "설명")

            `when`(memberRepository.findById(1L)).thenReturn(Optional.of(memberEntity))
            `when`(userGroupRepository.findById(100L)).thenReturn(Optional.of(groupEntity))

            // existsById 복합키 매킹을 True로 유도
            val targetId = UserGroupMemberId(member = 1L, group = 100L)
            `when`(userGroupMemberRepository.existsById(targetId)).thenReturn(true)

            // when & then
            assertThatThrownBy { adminService.assignGroup(command) }
                .isInstanceOf(BusinessException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_JOINED_GROUP)
        }
    }

    @Nested
    @DisplayName("어드민 강제 추방 테스트")
    inner class ForceWithdrawTest {

        @Test
        @DisplayName("어드민이 회원을 추방하면 유저의 상태를 탈퇴로 바꾸고 리프레시 토큰과 캐시를 즉시 연쇄 파기한다")
        fun forceWithdrawSuccess() {
            // given
            val memberId = 99L
            val memberEntity = MemberEntity(id = memberId, email = "spammer@company.com", password = "pwd", userName = "분탕러", role = Role.ROLE_MEMBER.toString())

            `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(memberEntity))
            `when`(memberRepository.save(any())).thenAnswer { it.arguments[0] }

            // when
            adminService.forceWithdrawMemberByAdmin(memberId)

            // then
            // 1. DB 정합성: 탈퇴 처리 상태가 통과되어 entity로 역변환 저장되었는지 검증
            verify(memberRepository).save(any())

            // 2. 인프라 정합성: 분탕 유저의 세션 인프라 자원을 가차없이 밀어버렸는지 추적 검증
            verify(userCacheRepository).deleteUserCache("spammer@company.com")
            verify(tokenRepository).deleteRefreshToken("spammer@company.com")
        }
    }
}