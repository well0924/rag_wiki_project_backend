package com.example.wiki_project.member.application

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.member.application.dto.UserGroupAssignCommand
import com.example.wiki_project.member.domain.entity.UserGroupMember
import com.example.wiki_project.member.domain.entity.UserGroupMemberId
import com.example.wiki_project.member.domain.mapper.toEntity
import com.example.wiki_project.member.domain.mapper.toModel
import com.example.wiki_project.member.domain.model.MemberModel
import com.example.wiki_project.member.domain.model.Role
import com.example.wiki_project.member.infrastructure.MemberRepository
import com.example.wiki_project.member.infrastructure.UserGroupMemberRepository
import com.example.wiki_project.member.infrastructure.UserGroupRepository
import com.example.wiki_project.member.infrastructure.security.RedisTokenRepository
import com.example.wiki_project.member.infrastructure.security.UserCacheRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminService(
    private val memberRepository: MemberRepository,
    private val userGroupRepository: UserGroupRepository,
    private val userGroupMemberRepository: UserGroupMemberRepository,
    private val tokenRepository: RedisTokenRepository,
    private val userCacheRepository: UserCacheRepository
) {

    /**
     * 관리자 권한 승격 (기존 MemberService에서 이동)
     */
    @Transactional
    fun promoteToAdmin(id: Long): MemberModel {
        val memberEntity = memberRepository.findById(id)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

        val memberModel = memberEntity.toModel()
        val promotedModel = memberModel.promoteToAdmin()

        memberRepository.save(promotedModel.toEntity())
        userCacheRepository.deleteUserCache(memberModel.email) // 보안을 위한 즉시 캐시 파기
        tokenRepository.deleteRefreshToken(memberModel.email)
        return promotedModel
    }

    /**
     * 회원을 특정 그룹에 강제 지정 (기존 MemberService에서 이동)
     */
    @Transactional
    fun assignGroup(command: UserGroupAssignCommand) {
        val memberEntity = memberRepository.findById(command.memberId)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

        val memberModel = memberEntity.toModel()

        if (memberModel.isDeleted) {
            throw BusinessException(ErrorCode.ALREADY_WITHDRAWN_MEMBER)
        }

        if (memberModel.role == Role.ROLE_ADMIN) {
            throw BusinessException(ErrorCode.ADMIN_CANNOT_BE_ASSIGNED_TO_GROUP)
        }

        val groupEntity = userGroupRepository.findById(command.groupId)
            .orElseThrow { BusinessException(ErrorCode.GROUP_NOT_FOUND) }

        val isAlreadyJoined = userGroupMemberRepository.existsById(
            UserGroupMemberId(member = memberModel.id, group = groupEntity.id)
        )
        if (isAlreadyJoined) {
            throw BusinessException(ErrorCode.ALREADY_JOINED_GROUP)
        }

        val userGroupMember = UserGroupMember(
            member = memberEntity,
            group = groupEntity
        )
        userGroupMemberRepository.save(userGroupMember)

        userCacheRepository.deleteUserCache(memberModel.email)
    }

    /**
     * 어드민에 의한 회원 강제 추방 (Soft Delete + 세션 추방)
     */
    @Transactional
    fun forceWithdrawMemberByAdmin(id: Long) {
        val memberEntity = memberRepository.findById(id)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

        val memberModel = memberEntity.toModel()

        // 도메인 모델의 탈퇴 규칙(isDeleted = true) 재사용
        val withdrawnModel = memberModel.withdraw()
        memberRepository.save(withdrawnModel.toEntity())

        // 추방된 회원의 Refresh Token과 인증 캐시를 즉시 삭제
        // → 다음 요청부터 인증 필터가 DB의 최신 상태(탈퇴)를 읽어 차단하고, 토큰 재발급도 불가
        userCacheRepository.deleteUserCache(memberModel.email)
        tokenRepository.deleteRefreshToken(memberModel.email)

        // (필요 시) 감사 로그(Audit Log) 저장
    }
}