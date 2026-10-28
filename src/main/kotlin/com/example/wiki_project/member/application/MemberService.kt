package com.example.wiki_project.member.application

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.member.application.dto.*
import com.example.wiki_project.member.domain.model.MemberModel
import com.example.wiki_project.member.domain.model.Role
import com.example.wiki_project.member.infrastructure.MemberRepository
import com.example.wiki_project.member.domain.mapper.toModel
import com.example.wiki_project.member.domain.mapper.toEntity
import com.example.wiki_project.member.infrastructure.security.RedisTokenRepository
import com.example.wiki_project.member.infrastructure.security.UserCacheRepository
import com.example.wiki_project.member.infrastructure.security.jwt.JwtTokenProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val userCacheRepository: UserCacheRepository,
    private val tokenRepository: RedisTokenRepository,
    private val jwtTokenProvider: JwtTokenProvider
) {

    /**
     * 회원 가입
     */
    @Transactional
    fun registerMember(command: UserCreateCommand): MemberModel {
        if (memberRepository.findByEmail(command.email) != null) {
            throw BusinessException(ErrorCode.DUPLICATE_EMAIL)
        }

        val memberModel = MemberModel(
            email = command.email,
            password = passwordEncoder.encode(command.password),
            userName = command.userName,
            role = Role.ROLE_MEMBER
        )

        val savedEntity = memberRepository.save(memberModel.toEntity())
        return savedEntity.toModel()
    }

    /**
     * 회원 단건 조회
     */
    @Transactional(readOnly = true)
    fun getMemberById(id: Long): MemberModel {
        val entity = memberRepository.findById(id)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

        return entity.toModel()
    }

    /**
     * 회원 프로필 수정 (이름 등 통합 수정)
     */
    @Transactional
    fun updateMember(id: Long, command: UserUpdateCommand): MemberModel {
        val memberEntity = memberRepository.findById(id)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

        val memberModel = memberEntity.toModel()
        val updatedModel = memberModel.updateProfile(command.userName)

        memberRepository.save(updatedModel.toEntity())
        userCacheRepository.deleteUserCache(memberModel.email) // 캐시 파기

        return updatedModel
    }

    /**
     * 회원 이름 변경
     */
    @Transactional
    fun updateUserName(id: Long, newName: String): MemberModel {
        val memberEntity = memberRepository.findById(id)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

        val memberModel = memberEntity.toModel()
        val updatedModel = memberModel.changeUserName(newName)

        memberRepository.save(updatedModel.toEntity())
        userCacheRepository.deleteUserCache(memberModel.email)

        return updatedModel
    }

    /**
     * 회원 삭제 (Soft Delete 탈퇴 + 세션 강제 파기)
     */
    @Transactional
    fun deleteMember(id: Long, accessToken: String) {
        val memberEntity = memberRepository.findById(id)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

        val memberModel = memberEntity.toModel()
        val withdrawnModel = memberModel.withdraw()

        memberRepository.save(withdrawnModel.toEntity())

        // 탈퇴 즉시 세션 파기: 캐시 / Refresh Token 삭제 + Access Token 블랙리스트 등록
        userCacheRepository.deleteUserCache(memberModel.email)
        tokenRepository.deleteRefreshToken(memberModel.email)
        val remainExpirationMs = jwtTokenProvider.getExpiration(accessToken)
        if (remainExpirationMs > 0) {
            tokenRepository.addToBlacklist(accessToken, remainExpirationMs)
        }
    }
}