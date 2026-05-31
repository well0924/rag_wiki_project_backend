package com.example.wiki_project.member.infrastructure.security.oauth

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority

class GoogleOAuth2AuthenticationToken(
    private val email: String,
    private val name: String,
    authorities: Collection<GrantedAuthority> = emptyList()
) : AbstractAuthenticationToken(authorities) {

    init {
        // 이 생성자로 만들어진 토큰은 아직 검증 전 상태임
        if (authorities.isNotEmpty()) {
            isAuthenticated = true
        }
    }

    override fun getCredentials(): Any? = null
    override fun getPrincipal(): String = email
    fun getNameAttribute(): String = name
}