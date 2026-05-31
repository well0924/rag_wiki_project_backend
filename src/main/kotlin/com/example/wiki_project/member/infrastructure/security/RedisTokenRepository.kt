package com.example.wiki_project.member.infrastructure.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class RedisTokenRepository(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${jwt.refresh-expiration}") private val refreshExpiration: Long
) {

    /**
     * Refresh Token 저장
     */
    fun saveRefreshToken(email: String, refreshToken: String) {
        redisTemplate.opsForValue().set(
            "refresh:$email",
            refreshToken,
            refreshExpiration,
            TimeUnit.MILLISECONDS
        )
    }

    /**
     * Refresh Token 조회
     */
    fun getRefreshToken(email: String): String? {
        return redisTemplate.opsForValue().get("refresh:$email")
    }

    /**
     * Refresh Token 삭제 (로그아웃용)
     */
    fun deleteRefreshToken(email: String) {
        redisTemplate.delete("refresh:$email")
    }

    /**
     *  Access Token 블랙리스트 추가
     */
    fun addToBlacklist(accessToken: String, expirationMs: Long) {
        redisTemplate.opsForValue().set(
            "blacklist:$accessToken",
            "logout",
            expirationMs,
            TimeUnit.MILLISECONDS
        )
    }
}