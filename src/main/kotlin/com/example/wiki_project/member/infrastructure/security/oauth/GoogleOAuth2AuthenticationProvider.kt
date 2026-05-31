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

        // 1. DB 뒤져서 가입 여부 체크 후, 소셜 신규 유저면 가입(Just-In-Time) 처리
        val memberEntity = memberRepository.findByEmail(email)
            ?: memberRepository.save(
                MemberModel(
                    email = email,
                    password = UUID.randomUUID().toString(), // 소셜 유저는 더미 패스워드 주입
                    userName = name,
                    role = Role.ROLE_MEMBER
                ).toEntity()
            )

        val memberModel = memberEntity.toModel()

        // 2. [요구사항 방어] 구글 계정이더라도 이미 Soft Delete(탈퇴)된 회원이라면 로그인 전면 차단
        if (memberModel.isDeleted) {
            throw BadCredentialsException("탈퇴 처리된 소셜 계정입니다.")
        }

        // 3. 우리의 시큐리티 규격 표준인 CustomUserDetails로 포장
        // TODO: 필요시 아까 만든 경량 쿼리 서비스(MemberQueryService) 주입받아 actualGroupIds 박아주기
        val authInfo = memberQueryService.findGroupIds(memberModel)
        // 캐시 적재 추가
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
        // 4. 최종 인증 성립 토큰 반환 (인증 완료 상태 마킹)
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