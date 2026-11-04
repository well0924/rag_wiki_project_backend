package com.example.wiki_project.member.application

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.member.application.dto.GroupMemberResponse
import com.example.wiki_project.member.domain.entity.UserGroup
import com.example.wiki_project.member.domain.entity.UserGroupMember
import com.example.wiki_project.member.domain.entity.UserGroupMemberId
import com.example.wiki_project.member.domain.mapper.toEntity
import com.example.wiki_project.member.domain.mapper.toModel
import com.example.wiki_project.member.domain.model.GroupRole
import com.example.wiki_project.member.domain.model.UserGroupModel
import com.example.wiki_project.member.infrastructure.MemberRepository
import com.example.wiki_project.member.infrastructure.UserGroupMemberRepository
import com.example.wiki_project.member.infrastructure.UserGroupRepository
import com.example.wiki_project.member.infrastructure.security.UserCacheRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GroupService(
    private val userGroupRepository: UserGroupRepository,
    private val userGroupMemberRepository: UserGroupMemberRepository,
    private val memberRepository: MemberRepository,
    private val userCacheRepository: UserCacheRepository
) {

    /**
     * 그룹 생성
     * 로그인한 회원 누구나 그룹(워크스페이스)을 만들 수 있고, 만든 사람이 OWNER가 됨
     */
    @Transactional
    fun createGroup(memberId: Long, groupName: String, description: String?): UserGroupModel {
        // 중복 그룹명 검증
        if (userGroupRepository.findByGroupName(groupName) != null) {
            throw BusinessException(ErrorCode.DUPLICATE_GROUP_NAME)
        }

        // 생성 주체인 회원이 존재하는지 검증 및 Entity 확보
        val memberEntity = memberRepository.findById(memberId)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

        // 도메인 모델 생성 및 유효성 검증 트리거 (초기 생성 시에도 검증 적용)
        val groupModel = UserGroupModel(groupName = groupName, description = description)
        // 수정 시와 동일한 이름 / 설명 검증 규칙 적용
        val validatedModel = groupModel.updateGroupInfo(groupName, description)

        // 그룹 저장
        val savedGroupEntity = userGroupRepository.save(validatedModel.toEntity())

        // 생성자를 OWNER로 등록
        userGroupMemberRepository.save(
            UserGroupMember(
                member = memberEntity,
                group = savedGroupEntity,
                role = GroupRole.OWNER
            )
        )

        // 소속 그룹이 바뀌었으므로 인증 캐시 파기 → 다음 요청부터 새 groupIds 반영
        userCacheRepository.deleteUserCache(memberEntity.email)

        // 도메인 규칙에 따라 다시 모델로 변환하여 반환
        return savedGroupEntity.toModel()
    }

    /**
     * 내 소속 그룹 목록 조회
     * 메인 페이지 진입 시 해당 유저가 속한 모든 위키 그룹(워크스페이스)을 사이드바에 바인딩
     */
    @Transactional(readOnly = true)
    fun getMyGroups(memberId: Long): List<UserGroupModel> {
        // 회원 ID로 소속 그룹 매핑 조회
        val mappingList = userGroupMemberRepository.findByMemberId(memberId)

        // 매핑 엔티티에서 그룹 엔티티를 꺼내 도메인 모델로 변환
        return mappingList.map { it.group.toModel() }
    }

    /**
     * 그룹 정보 수정 (OWNER만)
     */
    @Transactional
    fun updateGroup(groupId: Long, requesterId: Long, newGroupName: String, newDescription: String?): UserGroupModel {
        val groupEntity = findGroup(groupId)
        requireOwner(groupId, requesterId)

        // 변경하려는 이름이 이미 존재하는지 유효성 체크 (단, 내 현재 그룹명은 제외)
        val existingGroup = userGroupRepository.findByGroupName(newGroupName)
        if (existingGroup != null && existingGroup.id != groupId) {
            throw BusinessException(ErrorCode.DUPLICATE_GROUP_NAME)
        }

        // 엔티티를 도메인 모델로 변환
        val currentModel = groupEntity.toModel()

        // 도메인 모델에서 유효성 검증 후 변경된 복사본 반환
        val updatedModel = currentModel.updateGroupInfo(newGroupName, newDescription)

        // 검증 완료된 도메인 데이터를 다시 엔티티로 변환하여 영속성 컨텍스트 반영
        val savedEntity = userGroupRepository.save(updatedModel.toEntity())
        return savedEntity.toModel()
    }

    /**
     * 그룹 삭제 (OWNER만)
     */
    @Transactional
    fun deleteGroup(groupId: Long, requesterId: Long) {
        val groupEntity = findGroup(groupId)
        requireOwner(groupId, requesterId)

        // 그룹 해체로 권한이 회수되는 소속 회원들의 인증 캐시 파기
        userGroupMemberRepository.findByGroup(groupEntity)
            .forEach { userCacheRepository.deleteUserCache(it.member.email) }

        // 그룹 멤버 매핑을 먼저 삭제 (FK 제약)
        userGroupMemberRepository.deleteByGroup(groupEntity)

        // 그룹 삭제
        userGroupRepository.delete(groupEntity)
    }

    /**
     * 이메일로 멤버 추가 (OWNER만)
     * 초대 수락 단계 없이 가입된 회원을 바로 MEMBER로 추가함
     */
    @Transactional
    fun addMemberByEmail(groupId: Long, requesterId: Long, email: String): GroupMemberResponse {
        val groupEntity = findGroup(groupId)
        requireOwner(groupId, requesterId)

        val targetEntity = memberRepository.findByEmail(email.trim())
            ?: throw BusinessException(ErrorCode.MEMBER_EMAIL_NOT_FOUND)
        val targetModel = targetEntity.toModel()

        if (targetModel.isDeleted) {
            throw BusinessException(ErrorCode.ALREADY_WITHDRAWN_MEMBER)
        }

        if (userGroupMemberRepository.existsById(UserGroupMemberId(member = targetModel.id, group = groupId))) {
            throw BusinessException(ErrorCode.ALREADY_JOINED_GROUP)
        }

        userGroupMemberRepository.save(
            UserGroupMember(member = targetEntity, group = groupEntity, role = GroupRole.MEMBER)
        )

        // 추가된 회원의 인증 캐시 파기 → 다음 요청부터 이 그룹 문서 검색 가능
        userCacheRepository.deleteUserCache(targetEntity.email)

        return GroupMemberResponse(
            memberId = targetModel.id ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND),
            email = targetEntity.email,
            userName = targetEntity.userName,
            role = GroupRole.MEMBER
        )
    }

    /**
     * 멤버 제거 (OWNER만, OWNER 자신은 제거 불가)
     */
    @Transactional
    fun removeMember(groupId: Long, requesterId: Long, targetMemberId: Long) {
        findGroup(groupId)
        requireOwner(groupId, requesterId)

        val mapping = userGroupMemberRepository.findById(UserGroupMemberId(member = targetMemberId, group = groupId))
            .orElseThrow { BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND) }

        // OWNER가 빠지면 그룹을 관리할 사람이 없어지므로 차단
        if (mapping.role == GroupRole.OWNER) {
            throw BusinessException(ErrorCode.GROUP_OWNER_CANNOT_BE_REMOVED)
        }

        userGroupMemberRepository.delete(mapping)

        // 권한이 회수되었으므로 인증 캐시 파기 → 다음 요청부터 이 그룹 문서 검색 불가
        userCacheRepository.deleteUserCache(mapping.member.email)
    }

    /**
     * 그룹 멤버 목록 조회 (그룹에 속한 회원이면 누구나)
     */
    @Transactional(readOnly = true)
    fun getGroupMembers(groupId: Long, requesterId: Long): List<GroupMemberResponse> {
        findGroup(groupId)

        val mappings = userGroupMemberRepository.findWithMemberByGroupId(groupId)
        if (mappings.none { it.member.id == requesterId }) {
            throw BusinessException(ErrorCode.GROUP_MEMBER_ONLY)
        }

        return mappings.map {
            GroupMemberResponse(
                memberId = it.member.id ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND),
                email = it.member.email,
                userName = it.member.userName,
                role = it.role
            )
        }
    }

    private fun findGroup(groupId: Long): UserGroup =
        userGroupRepository.findById(groupId)
            .orElseThrow { BusinessException(ErrorCode.GROUP_NOT_FOUND) }

    // 요청자가 이 그룹의 OWNER인지 확인 (소속이 아니거나 MEMBER면 403)
    private fun requireOwner(groupId: Long, requesterId: Long) {
        val mapping = userGroupMemberRepository.findById(UserGroupMemberId(member = requesterId, group = groupId))
            .orElse(null)
        if (mapping?.role != GroupRole.OWNER) {
            throw BusinessException(ErrorCode.GROUP_OWNER_ONLY)
        }
    }
}
