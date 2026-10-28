package com.example.wiki_project.domain.member

import com.example.wiki_project.member.infrastructure.security.oauth.CustomOAuth2UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.OAuth2AuthenticationException

class CustomOAuth2UserServiceTest {

    private fun googleAttributes(email: String, hd: String? = null, verified: Boolean = true): Map<String, Any> =
        buildMap {
            put("sub", "1234567890")
            put("email", email)
            put("name", "홍길동")
            put("email_verified", verified)
            hd?.let { put("hd", it) }
        }

    @Test
    @DisplayName("허용 도메인 설정이 비어 있으면 개인 구글 계정도 로그인할 수 있다 (시연용 기본값)")
    fun allowAnyGoogleAccountWhenDomainsEmpty() {
        val service = CustomOAuth2UserService("")

        val (email, name) = service.extractAccount("google", googleAttributes("tester@gmail.com"))

        assertThat(email).isEqualTo("tester@gmail.com")
        assertThat(name).isEqualTo("홍길동")
    }

    @Test
    @DisplayName("이메일 인증이 안 된 구글 계정은 도메인 설정과 관계없이 거부한다 (이메일 기반 계정 탈취 방지)")
    fun rejectUnverifiedEmail() {
        val service = CustomOAuth2UserService("")

        assertThatThrownBy { service.extractAccount("google", googleAttributes("tester@gmail.com", verified = false)) }
            .isInstanceOf(OAuth2AuthenticationException::class.java)
            .hasMessageContaining("이메일 인증")
    }

    @Test
    @DisplayName("허용 도메인이 지정되면 해당 Google Workspace(hd) 소속 계정만 통과한다")
    fun allowOnlyConfiguredWorkspaceDomain() {
        val service = CustomOAuth2UserService(" Company.com , company.co.kr ")

        assertThat(service.extractAccount("google", googleAttributes("dev@company.com", hd = "company.com")).first)
            .isEqualTo("dev@company.com")
        assertThat(service.extractAccount("google", googleAttributes("dev@company.co.kr", hd = "company.co.kr")).first)
            .isEqualTo("dev@company.co.kr")
    }

    @Test
    @DisplayName("허용 도메인이 지정되면 개인 gmail 계정(hd 없음)과 다른 회사 계정은 거부한다")
    fun rejectOtherDomains() {
        val service = CustomOAuth2UserService("company.com")

        listOf(
            googleAttributes("tester@gmail.com"),
            googleAttributes("dev@other.com", hd = "other.com"),
            // Workspace 소속이 맞더라도 이메일 도메인이 다르면 거부 (보조 도메인 등 예외 상황 차단)
            googleAttributes("dev@other.com", hd = "company.com")
        ).forEach { attributes ->
            assertThatThrownBy { service.extractAccount("google", attributes) }
                .isInstanceOf(OAuth2AuthenticationException::class.java)
                .hasMessageContaining("company.com")
        }
    }

    @Test
    @DisplayName("지원하지 않는 공급자나 이메일이 없는 응답은 인증 예외로 거부한다")
    fun rejectUnsupportedProviderOrMissingEmail() {
        val service = CustomOAuth2UserService("")

        assertThatThrownBy { service.extractAccount("github", googleAttributes("a@gmail.com")) }
            .isInstanceOf(OAuth2AuthenticationException::class.java)
        assertThatThrownBy { service.extractAccount("google", mapOf("sub" to "1", "email_verified" to true)) }
            .isInstanceOf(OAuth2AuthenticationException::class.java)
    }
}
