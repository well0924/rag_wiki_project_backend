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

    // 토큰 타입을 지원하는 Provider 를 찾아 인증을 맡기는 ProviderManager 에 위임
    private val delegate: ProviderManager = ProviderManager(
        listOf(customAuthenticationProvider, googleOAuth2AuthenticationProvider)
    )

    override fun authenticate(authentication: Authentication): Authentication {
        return delegate.authenticate(authentication)
    }
}