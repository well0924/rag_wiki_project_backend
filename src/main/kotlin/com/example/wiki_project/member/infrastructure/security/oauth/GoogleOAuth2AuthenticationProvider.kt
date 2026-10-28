package com.example.wiki_project.member.infrastructure.security.oauth

import com.example.wiki_project.member.application.MemberQueryService
import com.example.wiki_project.member.application.dto.UserCacheData
import com.example.wiki_project.member.domain.mapper.toEntity
import com.example.wiki_project.member.domain.mapper.toModel
import com.example.wiki_project.member.domain.model.MemberModel
import com.example.wiki_project.member.domain.model.Role
import com.example.wiki_project.member.infrastructure.MemberRepository
import com.example.wiki_project.member.infrastructure.security.UserCacheRepository
import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetails
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class GoogleOAuth2AuthenticationProvider(
    private val memberRepository: MemberRepository,
    private val userCacheRepository: UserCacheRepository,
    private val memberQueryService:MemberQueryService
) : AuthenticationProvider {

    override fun authenticate(authentication: Authentication): Authentication {
        val token = authentication as GoogleOAuth2AuthenticationToken
        val email = token.principal
        val name = token.getNameAttribute()

        // 1. 이메일로 회원 조회, 없으면 자동 가입(JIT)
        val memberEntity = memberRepository.findByEmail(email)
            ?: memberRepository.save(
                MemberModel(
                    email = email,
                    password = UUID.randomUUID().toString(), // 소셜 회원은 비밀번호 로그인을 쓰지 않으므로 임의 값 저장
                    userName = name,
                    role = Role.ROLE_MEMBER
                ).toEntity()
            )

        val memberModel = memberEntity.toModel()

        // 2. 탈퇴(Soft Delete)한 회원은 구글 로그인도 차단
        if (memberModel.isDeleted) {
            throw BadCredentialsException("탈퇴 처리된 소셜 계정입니다.")
        }

        // 3. 소속 그룹 / 관리자 여부를 조회하여 CustomUserDetails 구성
        val authInfo = memberQueryService.findGroupIds(memberModel)
        // 인증 캐시 적재 (이후 요청은 DB 조회 없이 인증)
        userCacheRepository.saveUserCache(email, UserCacheData(
            member = memberModel,
            groupIds = authInfo.groupIds,
            isAdmin = authInfo.isAdmin
        ))
        val customUserDetails = CustomUserDetails(
            member = memberModel,
            groupIds = authInfo.groupIds,
            isAdmin = authInfo.isAdmin
        )
        // 4. 인증 완료 토큰 반환
        return UsernamePasswordAuthenticationToken(
            customUserDetails,
            null,
            customUserDetails.authorities
        )
    }

    // 이 프로바이더는 구글 전용 소셜 토큰이 들어왔을 때만 작동함
    override fun supports(authentication: Class<*>): Boolean {
        return GoogleOAuth2AuthenticationToken::class.java.isAssignableFrom(authentication)
    }
}