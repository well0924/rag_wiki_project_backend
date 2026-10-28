package com.example.wiki_project.domain.member

import com.example.wiki_project.member.application.dto.TokenResponse
import com.example.wiki_project.member.infrastructure.security.OAuth2LoginCodeRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * 실제 Redis(로컬 / CI 서비스 컨테이너)에 붙여 Lua 스크립트의 원자적 1회 교환을 검증
 * - 스프링 컨텍스트 없이 커넥션 팩토리만 직접 생성
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OAuth2LoginCodeRepositoryTest {

    private val connectionFactory = LettuceConnectionFactory("localhost", 6379)
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var repository: OAuth2LoginCodeRepository

    @BeforeAll
    fun setUp() {
        connectionFactory.afterPropertiesSet()
        connectionFactory.start()
        redisTemplate = StringRedisTemplate(connectionFactory)
        repository = OAuth2LoginCodeRepository(redisTemplate)
    }

    @AfterAll
    fun tearDown() {
        connectionFactory.destroy()
    }

    @Test
    @DisplayName("발급한 코드는 한 번만 토큰으로 교환되고, 두 번째 교환은 실패한다")
    fun codeCanBeConsumedOnlyOnce() {
        val tokens = TokenResponse("access.jwt.token", "refresh.jwt.token")

        val code = repository.issue(tokens)

        assertThat(code).hasSizeGreaterThanOrEqualTo(43) // 32바이트 난수의 URL-safe Base64
        assertThat(repository.consume(code)).isEqualTo(tokens)
        assertThat(repository.consume(code)).isNull()
    }

    @Test
    @DisplayName("코드는 30초 TTL 로 저장되고, 매번 다른 값으로 발급된다")
    fun codeHasShortTtlAndIsUnique() {
        val tokens = TokenResponse("a", "b")

        val first = repository.issue(tokens)
        val second = repository.issue(tokens)

        assertThat(first).isNotEqualTo(second)
        val ttl = redisTemplate.getExpire("oauth2:code:$first")
        assertThat(ttl).isBetween(1L, OAuth2LoginCodeRepository.CODE_TTL.seconds)
        listOf(first, second).forEach { repository.consume(it) }
    }

    @Test
    @DisplayName("존재하지 않는 코드는 null 을 반환한다")
    fun unknownCodeReturnsNull() {
        assertThat(repository.consume("unknown-code")).isNull()
    }
}
