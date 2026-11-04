package com.example.wiki_project.domain.member

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.member.application.GroupService
import com.example.wiki_project.member.domain.entity.MemberEntity
import com.example.wiki_project.member.domain.entity.UserGroup
import com.example.wiki_project.member.domain.entity.UserGroupMember
import com.example.wiki_project.member.domain.entity.UserGroupMemberId
import com.example.wiki_project.member.domain.model.GroupRole
import com.example.wiki_project.member.infrastructure.MemberRepository
import com.example.wiki_project.member.infrastructure.UserGroupMemberRepository
import com.example.wiki_project.member.infrastructure.UserGroupRepository
import com.example.wiki_project.member.infrastructure.security.UserCacheRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class UserGroupServiceTest {

    @Mock private lateinit var userGroupRepository: UserGroupRepository
    @Mock private lateinit var userGroupMemberRepository: UserGroupMemberRepository
    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var userCacheRepository: UserCacheRepository

    @InjectMocks
    private lateinit var groupService: GroupService

    // 테스트용 공통 가짜 데이터
    private val memberId = 9L
    private val groupId = 100L
    private val targetId = 20L
    private val outsiderId = 30L
    private val mockMemberEntity = MemberEntity(
        id = memberId,
        email = "kyungbin@company.com",
        password = "hashed_password",
        userName = "양경빈",
        role = "ROLE_MEMBER"
    )

    @Nested
    @DisplayName("그룹 생성 테스트")
    inner class CreateGroupTest {

        @Test
        @DisplayName("그룹 생성 성공 시 UserGroup 엔티티에 저장 후 매핑 테이블에도 데이터가 저장이 되어야 한다.")
        fun createGroupSuccess() {
            // given
            val groupName = "코어-RAG팀"
            val description = "AI RAG 위키 격리 그룹"
            val expectedGroup = UserGroup(id = groupId, groupName = groupName, description = description)

            // doReturn().whenever() 스타일로 안전하게 선언하고 any에 명확한 타입(제네릭) 명시
            doReturn(null).whenever(userGroupRepository).findByGroupName(groupName)
            doReturn(Optional.of(mockMemberEntity)).whenever(memberRepository).findById(memberId)

            // any<UserGroup>() 처럼 타입을 지정해서 Mockito가 확실히 인지하게 만듦
            doReturn(expectedGroup).whenever(userGroupRepository).save(any<UserGroup>())

            // 매핑 테이블 save도 any<UserGroupMember>() 명시 및 들어온 인자 그대로 뱉게 answer 처리
            whenever(userGroupMemberRepository.save(any<UserGroupMember>())).thenAnswer { it.arguments[0] }
            // when
            val result = groupService.createGroup(memberId, groupName, description)

            // then
            assertThat(result).isNotNull
            assertThat(result.id).isEqualTo(groupId)
            assertThat(result.groupName).isEqualTo(groupName)

            // org.mockito.kotlin.check 문법으로 매핑 테이블에 들어간 인자 팩트 체크
            verify(userGroupMemberRepository, times(1)).save(check { mapping ->
                assertThat(mapping.member.id).isEqualTo(memberId)
                assertThat(mapping.group.id).isEqualTo(groupId)
                assertThat(mapping.role).isEqualTo(GroupRole.OWNER) // 만든 사람은 OWNER
            })
        }

        @Test
        @DisplayName("그룹 생성 시 이미 존재하는 그룹명이면 DUPLICATE_GROUP_NAME 예외가 발생한다")
        fun createGroupFailDuplicateName() {
            // given
            val duplicateName = "이미있는그룹"
            whenever(userGroupRepository.findByGroupName(duplicateName)).doReturn(UserGroup(id = 1L, groupName = duplicateName))

            // when & then
            val exception = assertThrows<BusinessException> {
                groupService.createGroup(memberId, duplicateName, "설명")
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.DUPLICATE_GROUP_NAME)
        }
    }

    @Nested
    @DisplayName("내 그룹 목록 조회 테스트")
    inner class GetMyGroupsTest {

        @Test
        @DisplayName("내 소속 그룹 목록 조회 시 매핑 엔티티를 긁어와 도메인 모델 리스트로 올바르게 변환해야 한다")
        fun getMyGroupsSuccess() {
            // given
            val group1 = UserGroup(id = 100L, groupName = "개발팀")
            val group2 = UserGroup(id = 101L, groupName = "코어-RAG팀")
            val mockMappings = listOf(
                UserGroupMember(member = mockMemberEntity, group = group1),
                UserGroupMember(member = mockMemberEntity, group = group2)
            )

            whenever(userGroupMemberRepository.findByMemberId(memberId)).doReturn(mockMappings)

            // when
            val results = groupService.getMyGroups(memberId)

            // then
            assertThat(results).hasSize(2)
            assertThat(results[0].groupName).isEqualTo("개발팀")
            assertThat(results[1].groupName).isEqualTo("코어-RAG팀")
            verify(userGroupMemberRepository).findByMemberId(memberId)
        }
    }

    @Nested
    @DisplayName("그룹 정보 수정 테스트")
    inner class UpdateGroupTest {

        @Test
        @DisplayName("그룹 수정 성공 시 도메인 검증을 거쳐 정상 저장이 되는가?")
        fun updateGroupSuccess() {
            // given
            val currentGroup = UserGroup(id = groupId, groupName = "기존이름")
            val newName = "수정된이름"
            val expectedGroup = UserGroup(id = groupId, groupName = newName)

            doReturn(Optional.of(currentGroup)).whenever(userGroupRepository).findById(groupId)
            stubRole(memberId, currentGroup, GroupRole.OWNER)
            doReturn(null).whenever(userGroupRepository).findByGroupName(newName)

            // 여기서 save(any()) 매핑이 깨져서 에러가 났던 것임! 명확하게 any<UserGroup>() 지정
            doReturn(expectedGroup).whenever(userGroupRepository).save(any<UserGroup>())

            // when
            val result = groupService.updateGroup(groupId, memberId, newName, "설명")

            // then
            assertThat(result.groupName).isEqualTo(newName)
            verify(userGroupRepository).save(any())
        }

        @Test
        @DisplayName("MEMBER가 그룹을 수정하려 하면 GROUP_OWNER_ONLY로 차단된다")
        fun updateGroupFailWhenMember() {
            val currentGroup = UserGroup(id = groupId, groupName = "기존이름")
            whenever(userGroupRepository.findById(groupId)).doReturn(Optional.of(currentGroup))
            stubRole(memberId, currentGroup, GroupRole.MEMBER)

            val exception = assertThrows<BusinessException> {
                groupService.updateGroup(groupId, memberId, "수정된이름", "설명")
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.GROUP_OWNER_ONLY)
            verify(userGroupRepository, never()).save(any<UserGroup>())
        }
    }

    @Nested
    @DisplayName("그룹 삭제 테스트")
    inner class DeleteGroupTest {

        @Test
        @DisplayName("그룹 삭제 시 매핑 데이터선 제거 후 그룹 마스터가 삭제된다")
        fun deleteGroupSuccess() {
            // given
            val targetGroup = UserGroup(id = groupId, groupName = "삭제그룹")
            whenever(userGroupRepository.findById(groupId)).doReturn(Optional.of(targetGroup))
            stubRole(memberId, targetGroup, GroupRole.OWNER)

            // when
            groupService.deleteGroup(groupId, memberId)

            // then
            verify(userGroupMemberRepository).deleteByGroup(targetGroup)
            verify(userGroupRepository).delete(targetGroup)
        }

        @Test
        @DisplayName("그룹에 속하지 않은 회원이 삭제하려 하면 GROUP_OWNER_ONLY로 차단된다")
        fun deleteGroupFailWhenOutsider() {
            val targetGroup = UserGroup(id = groupId, groupName = "삭제그룹")
            whenever(userGroupRepository.findById(groupId)).doReturn(Optional.of(targetGroup))
            whenever(userGroupMemberRepository.findById(UserGroupMemberId(member = outsiderId, group = groupId)))
                .doReturn(Optional.empty())

            val exception = assertThrows<BusinessException> {
                groupService.deleteGroup(groupId, outsiderId)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.GROUP_OWNER_ONLY)
            verify(userGroupRepository, never()).delete(any<UserGroup>())
        }
    }

    @Nested
    @DisplayName("멤버 추가 / 제거 / 조회 테스트")
    inner class GroupMemberTest {

        private val group = UserGroup(id = groupId, groupName = "CS 스터디")
        private val targetEmail = "younghee@company.com"
        private val targetEntity = MemberEntity(
            id = targetId, email = targetEmail, password = "hashed", userName = "영희", role = "ROLE_MEMBER"
        )

        @Test
        @DisplayName("OWNER가 가입된 이메일로 추가하면 MEMBER로 저장하고 대상의 인증 캐시를 파기한다")
        fun addMemberSuccess() {
            whenever(userGroupRepository.findById(groupId)).doReturn(Optional.of(group))
            stubRole(memberId, group, GroupRole.OWNER)
            whenever(memberRepository.findByEmail(targetEmail)).doReturn(targetEntity)
            whenever(userGroupMemberRepository.existsById(UserGroupMemberId(member = targetId, group = groupId))).doReturn(false)
            whenever(userGroupMemberRepository.save(any<UserGroupMember>())).thenAnswer { it.arguments[0] }

            val result = groupService.addMemberByEmail(groupId, memberId, " $targetEmail ")

            assertThat(result.memberId).isEqualTo(targetId)
            assertThat(result.role).isEqualTo(GroupRole.MEMBER)
            verify(userGroupMemberRepository).save(check { mapping ->
                assertThat(mapping.member.id).isEqualTo(targetId)
                assertThat(mapping.role).isEqualTo(GroupRole.MEMBER)
            })
            verify(userCacheRepository).deleteUserCache(targetEmail)
        }

        @Test
        @DisplayName("가입되지 않은 이메일이면 MEMBER_EMAIL_NOT_FOUND")
        fun addMemberFailUnknownEmail() {
            whenever(userGroupRepository.findById(groupId)).doReturn(Optional.of(group))
            stubRole(memberId, group, GroupRole.OWNER)
            whenever(memberRepository.findByEmail("nobody@company.com")).doReturn(null)

            val exception = assertThrows<BusinessException> {
                groupService.addMemberByEmail(groupId, memberId, "nobody@company.com")
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.MEMBER_EMAIL_NOT_FOUND)
        }

        @Test
        @DisplayName("이미 소속된 회원이면 ALREADY_JOINED_GROUP")
        fun addMemberFailAlreadyJoined() {
            whenever(userGroupRepository.findById(groupId)).doReturn(Optional.of(group))
            stubRole(memberId, group, GroupRole.OWNER)
            whenever(memberRepository.findByEmail(targetEmail)).doReturn(targetEntity)
            whenever(userGroupMemberRepository.existsById(UserGroupMemberId(member = targetId, group = groupId))).doReturn(true)

            val exception = assertThrows<BusinessException> {
                groupService.addMemberByEmail(groupId, memberId, targetEmail)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.ALREADY_JOINED_GROUP)
        }

        @Test
        @DisplayName("MEMBER는 다른 회원을 추가할 수 없다")
        fun addMemberFailWhenRequesterIsMember() {
            whenever(userGroupRepository.findById(groupId)).doReturn(Optional.of(group))
            stubRole(memberId, group, GroupRole.MEMBER)

            val exception = assertThrows<BusinessException> {
                groupService.addMemberByEmail(groupId, memberId, targetEmail)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.GROUP_OWNER_ONLY)
            verify(userGroupMemberRepository, never()).save(any<UserGroupMember>())
        }

        @Test
        @DisplayName("OWNER가 MEMBER를 제거하면 매핑을 지우고 대상의 인증 캐시를 파기한다")
        fun removeMemberSuccess() {
            whenever(userGroupRepository.findById(groupId)).doReturn(Optional.of(group))
            stubRole(memberId, group, GroupRole.OWNER)
            val targetMapping = UserGroupMember(member = targetEntity, group = group, role = GroupRole.MEMBER)
            whenever(userGroupMemberRepository.findById(UserGroupMemberId(member = targetId, group = groupId)))
                .doReturn(Optional.of(targetMapping))

            groupService.removeMember(groupId, memberId, targetId)

            verify(userGroupMemberRepository).delete(targetMapping)
            verify(userCacheRepository).deleteUserCache(targetEmail)
        }

        @Test
        @DisplayName("OWNER 자신은 제거할 수 없다")
        fun removeMemberFailWhenTargetIsOwner() {
            whenever(userGroupRepository.findById(groupId)).doReturn(Optional.of(group))
            stubRole(memberId, group, GroupRole.OWNER)

            val exception = assertThrows<BusinessException> {
                groupService.removeMember(groupId, memberId, memberId)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.GROUP_OWNER_CANNOT_BE_REMOVED)
            verify(userGroupMemberRepository, never()).delete(any<UserGroupMember>())
        }

        @Test
        @DisplayName("그룹에 속하지 않은 회원은 멤버 목록을 볼 수 없다")
        fun getMembersFailWhenOutsider() {
            whenever(userGroupRepository.findById(groupId)).doReturn(Optional.of(group))
            whenever(userGroupMemberRepository.findWithMemberByGroupId(groupId)).doReturn(
                listOf(UserGroupMember(member = mockMemberEntity, group = group, role = GroupRole.OWNER))
            )

            val exception = assertThrows<BusinessException> {
                groupService.getGroupMembers(groupId, outsiderId)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.GROUP_MEMBER_ONLY)
        }

        @Test
        @DisplayName("그룹 멤버는 멤버 목록과 역할을 볼 수 있다")
        fun getMembersSuccess() {
            whenever(userGroupRepository.findById(groupId)).doReturn(Optional.of(group))
            whenever(userGroupMemberRepository.findWithMemberByGroupId(groupId)).doReturn(
                listOf(
                    UserGroupMember(member = mockMemberEntity, group = group, role = GroupRole.OWNER),
                    UserGroupMember(member = targetEntity, group = group, role = GroupRole.MEMBER)
                )
            )

            val result = groupService.getGroupMembers(groupId, targetId)

            assertThat(result).extracting("email").containsExactly("kyungbin@company.com", targetEmail)
            assertThat(result).extracting("role").containsExactly(GroupRole.OWNER, GroupRole.MEMBER)
        }
    }

    // 요청자의 그룹 내 역할을 스텁
    private fun stubRole(requesterId: Long, group: UserGroup, role: GroupRole) {
        val requester = if (requesterId == memberId) mockMemberEntity
        else MemberEntity(id = requesterId, email = "u$requesterId@company.com", password = "hashed", role = "ROLE_MEMBER")
        whenever(userGroupMemberRepository.findById(UserGroupMemberId(member = requesterId, group = group.id)))
            .doReturn(Optional.of(UserGroupMember(member = requester, group = group, role = role)))
    }
}