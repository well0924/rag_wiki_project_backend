package com.example.wiki_project.member.infrastructure.security.oauth

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class CustomOAuth2UserService(
    // 로그인을 허용할 회사 도메인 (콤마 구분, 예: company.com,company.co.kr)
    // - 비워 두면 모든 구글 계정 허용 (포트폴리오 시연 / 로컬 개발용)
    // - 지정하면 해당 Google Workspace 소속 계정만 자동 가입(JIT) 및 로그인 가능
    @Value("\${app.sso.allowed-domains:}") allowedDomains: String
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private val allowedDomains: Set<String> = allowedDomains.split(",")
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toSet()

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

        // [정규화 + 접근 제어 레이어] 공급자 스펙에 맞게 이메일과 이름을 가공하고 가입 가능 계정인지 검증
        val (email, name) = extractAccount(registrationId, attributes)

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

    /**
     * 공급자 응답에서 (이메일, 이름) 추출 + 로그인 허용 여부 검증
     * - 여기서 던진 OAuth2AuthenticationException 은 OAuth2FailureHandler 가 받아 로그인 화면에 메시지로 전달
     */
    fun extractAccount(registrationId: String, attributes: Map<String, Any>): Pair<String, String> {
        if (registrationId != "google") {
            throw denied("지원하지 않는 소셜 로그인 공급자입니다: $registrationId")
        }

        val email = attributes["email"] as? String
            ?: throw denied("구글 계정 정보에 이메일이 누락되었습니다.")
        val name = attributes["name"] as? String ?: "Google User"

        // 회원은 이메일로 식별되므로, 소유가 확인되지 않은 이메일로 기존 회원 계정에 로그인하는 것을 차단
        if (attributes["email_verified"] != true) {
            throw denied("이메일 인증이 완료되지 않은 구글 계정입니다.")
        }

        if (allowedDomains.isNotEmpty()) {
            // hd: Google Workspace 소속 도메인 (개인 gmail 계정에는 없음)
            val hostedDomain = (attributes["hd"] as? String)?.lowercase()
            val emailDomain = email.substringAfterLast("@").lowercase()
            if (hostedDomain !in allowedDomains || emailDomain !in allowedDomains) {
                throw denied("회사 계정(${allowedDomains.joinToString(", ")})으로만 로그인할 수 있습니다.")
            }
        }

        return Pair(email, name)
    }

    private fun denied(message: String) =
        OAuth2AuthenticationException(OAuth2Error("access_denied", message, null), message)
}
