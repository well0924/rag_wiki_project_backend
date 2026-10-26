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
        // 만약 초기 생성 시에도 글자 수 등 require 검증을 먹이고 싶다면 아래처럼 활용 가능
        val validatedModel = groupModel.updateGroupInfo(groupName, description)

        // RDB에 그룹 마스터 영속화
        val savedGroupEntity = userGroupRepository.save(validatedModel.toEntity())

        // 다대다 매핑 엔티티에 생성자-그룹 관계 맺어주고 저장
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
        // 이미 가지고 있던 레포지토리 메서드 활용
        val mappingList = userGroupMemberRepository.findByMemberId(memberId)

        // 매핑 테이블에서 객체 그래프를 타고 올라가 UserGroup 엔티티를 도메인 모델로 최종 치환
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

        // 엔티티를 순수 도메인 모델로 격리 변경
        val currentModel = groupEntity.toModel()

        // 도메인 내 require 유효성 검증 및 copy 로직 작동!
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

        // PostgreSQL 복합 PK 최적화가 적용된 매핑 관계선 데이터 선행 삭제
        userGroupMemberRepository.deleteByGroup(groupEntity)

        // 실제 그룹 마스터 데이터 삭제
        userGroupRepository.delete(groupEntity)
    }
}