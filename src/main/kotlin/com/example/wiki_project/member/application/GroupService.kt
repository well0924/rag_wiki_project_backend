package com.example.wiki_project.member.application

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.member.domain.entity.UserGroupMember
import com.example.wiki_project.member.domain.mapper.toEntity
import com.example.wiki_project.member.domain.mapper.toModel
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
     * 메인 대시보드에서 유저가 직접 새로운 권한 그룹을 생성할 때 실행
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

        // 생성자를 그룹 멤버로 등록
        userGroupMemberRepository.save(
            UserGroupMember(
                member = memberEntity,
                group = savedGroupEntity
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
     * 그룹 정보 및 설정 수정
     */
    @Transactional
    fun updateGroup(groupId: Long, newGroupName: String, newDescription: String?): UserGroupModel {
        val groupEntity = userGroupRepository.findById(groupId)
            .orElseThrow { BusinessException(ErrorCode.GROUP_NOT_FOUND) }

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
     * 그룹 파기 / 삭제
     */
    @Transactional
    fun deleteGroup(groupId: Long) {
        val groupEntity = userGroupRepository.findById(groupId)
            .orElseThrow { BusinessException(ErrorCode.GROUP_NOT_FOUND) }

        // 그룹 해체로 권한이 회수되는 소속 회원들의 인증 캐시 파기
        userGroupMemberRepository.findByGroup(groupEntity)
            .forEach { userCacheRepository.deleteUserCache(it.member.email) }

        // 그룹 멤버 매핑을 먼저 삭제 (FK 제약)
        userGroupMemberRepository.deleteByGroup(groupEntity)

        // 그룹 삭제
        userGroupRepository.delete(groupEntity)
    }
}