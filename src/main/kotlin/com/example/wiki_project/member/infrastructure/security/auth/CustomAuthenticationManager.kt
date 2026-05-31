package com.example.wiki_project.member.infrastructure.security.auth

import com.example.wiki_project.member.infrastructure.security.oauth.GoogleOAuth2AuthenticationProvider
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class CustomAuthenticationManager(
    private val customAuthenticationProvider: CustomAuthenticationProvider,
    private val googleOAuth2AuthenticationProvider: GoogleOAuth2AuthenticationProvider
) : AuthenticationManager {

    // 내부적으로 하이버네이트와 시큐리티가 지원하는 멀티 프로바이더 매니저를 위임하는 방식
    private val delegate: ProviderManager = ProviderManager(
        listOf(customAuthenticationProvider, googleOAuth2AuthenticationProvider)
    )

    override fun authenticate(authentication: Authentication): Authentication {
        return delegate.authenticate(authentication)
    }
}