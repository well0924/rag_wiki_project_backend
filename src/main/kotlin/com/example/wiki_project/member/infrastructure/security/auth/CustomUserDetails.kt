package com.example.wiki_project.member.infrastructure.security.auth

import com.example.wiki_project.member.domain.model.MemberModel
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class CustomUserDetails(
    val member: MemberModel,
    val groupIds: List<Long>, // 유저가 속한 그룹 ID 리스트 보관
    val isAdmin: Boolean
) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return listOf(SimpleGrantedAuthority(member.role.name))
    }

    override fun getPassword(): String = member.password

    override fun getUsername(): String = member.email

    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = !member.isDeleted
}