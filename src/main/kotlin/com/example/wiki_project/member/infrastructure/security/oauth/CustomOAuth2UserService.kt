package com.example.wiki_project.member.infrastructure.security.oauth

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class CustomOAuth2UserService: OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        // 1. 스프링 시큐리티 공식 기본 소셜 유저 로더 서비스 위임 생성
        val delegate = DefaultOAuth2UserService()

        // 2. 구글 API 서버를 직접 찔러서 로우 프로필 데이터(Attributes Map) 긁어오기
        val oAuth2User = delegate.loadUser(userRequest)

        // 3. 현재 어떤 소셜 서비스 공급자(Google 등)를 타고 들어왔는지 식별값 추출
        val registrationId = userRequest.clientRegistration.registrationId

        // 4. 구글 인증 시 고유 식별 키가 되는 필드명 추출 (구글의 경우 기본값 'sub')
        val userNameAttributeName = userRequest.clientRegistration
            .providerDetails.userInfoEndpoint.userNameAttributeName

        // 5. 구글이 던져준 로우 데이터 맵 정보 확보
        val attributes = oAuth2User.attributes

        // [정규화 레이어] 공급자 스펙에 맞게 이메일과 이름 정밀하게 가공 처리
        val (email, name) = when (registrationId) {
            "google" -> {
                val extractedEmail = attributes["email"] as? String
                    ?: throw IllegalArgumentException("구글 계정 정보에 이메일이 누락되었습니다.")
                val extractedName = attributes["name"] as? String ?: "Google User"
                Pair(extractedEmail, extractedName)
            }
            else -> throw IllegalArgumentException("지원하지 않는 소셜 로그인 공급자입니다: $registrationId")
        }

        // 6. 아까 만든 OAuth2SuccessHandler가 깨짐 없이 낚아챌 수 있도록 공통 Key 명세 매핑하여 반환
        return DefaultOAuth2User(
            listOf(SimpleGrantedAuthority("ROLE_MEMBER")), // 기본 유저 권한 부여
            attributes + mapOf(
                "extractedEmail" to email,
                "extractedName" to name
            ),
            userNameAttributeName
        )
    }
}