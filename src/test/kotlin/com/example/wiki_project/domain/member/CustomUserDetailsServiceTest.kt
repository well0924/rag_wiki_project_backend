package com.example.wiki_project.domain.member

import com.example.wiki_project.member.application.MemberQueryService
import com.example.wiki_project.member.application.dto.UserCacheData
import com.example.wiki_project.member.domain.model.MemberModel
import com.example.wiki_project.member.domain.model.Role
import com.example.wiki_project.member.infrastructure.MemberRepository
import com.example.wiki_project.member.infrastructure.security.UserCacheRepository
import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetails
import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetailsService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class CustomUserDetailsServiceTest {
    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var memberQueryService: MemberQueryService
    @Mock private lateinit var userCacheRepository: UserCacheRepository

    @InjectMocks
    private lateinit var customUserDetailsService: CustomUserDetailsService

    @Test
    @DisplayName("캐시 히트 시 DB 조회 없이 캐시에서 바로 반환한다")
    fun loadUserByUsername_cacheHit() {
        // given
        val email = "test@company.com"
        val memberModel = MemberModel(
            id = 1L, email = email, password = "encrypted", userName = "홍길동", role = Role.ROLE_MEMBER
        )
        val cachedData = UserCacheData(
            member = memberModel, groupIds = listOf(1L, 2L), isAdmin = false
        )

        `when`(userCacheRepository.getUserCache(email)).thenReturn(cachedData)

        // when
        val result = customUserDetailsService.loadUserByUsername(email) as CustomUserDetails

        // then
        assertThat(result.username).isEqualTo(email)
        assertThat(result.groupIds).containsExactly(1L, 2L)

        // 핵심: DB 조회가 단 한 번도 일어나지 않았는지 검증
        verify(memberRepository, never()).findByEmail(any())
        verify(memberQueryService, never()).findGroupIds(any())
    }
}