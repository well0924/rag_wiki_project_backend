package com.example.wiki_project.member.infrastructure.security

import com.example.wiki_project.member.application.dto.UserCacheData
import com.example.wiki_project.member.domain.model.MemberModel
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class UserCacheRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    private val cacheTtlMs = 1800000L // 30분 (Millis)

    private val cacheVersion = "v2"

    fun saveUserCache(email: String, data: UserCacheData) {
        val jsonString = objectMapper.writeValueAsString(data)
        redisTemplate.opsForValue().set(
            "user-cache:$cacheVersion:$email",
            jsonString, cacheTtlMs, TimeUnit.MILLISECONDS
        )
    }

    fun getUserCache(email: String): UserCacheData? {
        val jsonString = redisTemplate.opsForValue()
            .get("user-cache:$cacheVersion:$email") ?: return null
        return try {
            objectMapper.readValue(jsonString, UserCacheData::class.java)
        } catch (e: Exception) {
            redisTemplate.delete("user-cache:$cacheVersion:$email")
            null
        }
    }

    fun deleteUserCache(email: String) {
        redisTemplate.delete("user-cache:$cacheVersion:$email")
    }
}