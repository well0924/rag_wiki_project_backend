package com.example.wiki_project.member.infrastructure.security

import com.example.wiki_project.member.application.dto.TokenResponse
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

/**
 * OAuth2 로그인 일회용 교환 코드 저장소
 * - 소셜 로그인 성공 후 토큰을 리다이렉트 URL에 그대로 싣지 않고(브라우저 기록 / 서버 로그 / Referer 노출),
 *   짧은 수명의 코드만 넘긴 뒤 프론트가 POST 로 토큰을 교환하도록 함
 * - 코드는 30초 후 만료되며, 한 번 교환하면 즉시 삭제 (재사용 불가)
 */
@Component
class OAuth2LoginCodeRepository(
    private val redisTemplate: StringRedisTemplate
) {

    companion object {
        private const val KEY_PREFIX = "oauth2:code:"
        val CODE_TTL: Duration = Duration.ofSeconds(30)

        // GET + DEL 을 원자적으로 수행 (GETDEL 은 Redis 6.2+ 전용이라 Lua 로 대체 → 동시에 두 번 교환해도 한 번만 성공)
        private val CONSUME_SCRIPT = DefaultRedisScript(
            "local v = redis.call('GET', KEYS[1]) if v then redis.call('DEL', KEYS[1]) end return v",
            String::class.java
        )
    }

    private val secureRandom = SecureRandom()

    /**
     * 토큰 세트를 저장하고 추측 불가능한 일회용 코드 반환 (256bit 난수, URL-safe)
     */
    fun issue(tokens: TokenResponse): String {
        val bytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        val code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        // JWT 는 공백을 포함하지 않으므로 공백 구분자로 함께 저장
        redisTemplate.opsForValue().set(KEY_PREFIX + code, "${tokens.accessToken} ${tokens.refreshToken}", CODE_TTL)
        return code
    }

    /**
     * 코드를 토큰 세트로 교환 (없거나 만료 / 이미 사용된 코드면 null)
     */
    fun consume(code: String): TokenResponse? {
        // 키가 없으면 Lua 가 nil 을 반환 → null (Spring 시그니처는 non-null 로 보이므로 nullable 로 명시해 받음)
        val result: String? = redisTemplate.execute(CONSUME_SCRIPT, listOf(KEY_PREFIX + code))
        val value = result ?: return null
        val (accessToken, refreshToken) = value.split(" ", limit = 2)
        return TokenResponse(accessToken, refreshToken)
    }
}
