package com.example.wiki_project.member.infrastructure.security.auth

import com.example.wiki_project.member.application.MemberQueryService
import com.example.wiki_project.member.application.dto.UserCacheData
import com.example.wiki_project.member.domain.mapper.toModel
import com.example.wiki_project.member.infrastructure.MemberRepository
import com.example.wiki_project.member.infrastructure.security.UserCacheRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CustomUserDetailsService(
    private val memberRepository: MemberRepository,
    private val memberQueryService: MemberQueryService,
    private val userCacheRepository: UserCacheRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {

        // 캐시 히트 시 DB 완전 우회
        val cached = userCacheRepository.getUserCache(username)
        if (cached != null) {
            return CustomUserDetails(cached.member, cached.groupIds, cached.isAdmin)
        }

        // 캐시 미스 - DB 조회 1회
        val memberEntity = memberRepository.findByEmail(username)
            ?: throw UsernameNotFoundException("존재하지 않는 사용자 이메일입니다: $username")

        val memberModel = memberEntity.toModel()
        val authInfo = memberQueryService.findGroupIds(memberModel)

        // 캐시 적재
        userCacheRepository.saveUserCache(username, UserCacheData(
            member = memberModel,
            groupIds = authInfo.groupIds,
            isAdmin = authInfo.isAdmin
        ))

        return CustomUserDetails(memberModel, authInfo.groupIds, authInfo.isAdmin)
    }
}