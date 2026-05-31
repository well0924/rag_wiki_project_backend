package com.example.wiki_project.common.redis

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.security.jackson2.SecurityJackson2Modules
import java.time.Duration

@Configuration
@EnableCaching
class RedisConfig {

    /**
     * 1. 기본 StringRedisTemplate 빈 등록
     * 블랙리스트나 리프레시 토큰처럼 단순 String Key-Value 조작할 때 사용
     */
    @Bean
    fun stringRedisTemplate(connectionFactory: RedisConnectionFactory): StringRedisTemplate {
        return StringRedisTemplate(connectionFactory)
    }

    /**
     * 2. @Cacheable 애노테이션 전용 RedisCacheManager 설정 (가장 중요)
     */
    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): RedisCacheManager {
        // 코틀린 및 시큐리티 코어 직렬화를 완벽 지원하는 커스텀 ObjectMapper 빌드
        val objectMapper = ObjectMapper().apply {
            registerKotlinModule() // 코틀린 역직렬화 에러(기본생성자 문제) 해결
            registerModules(SecurityJackson2Modules.getModules(this.javaClass.classLoader)) // 시큐리티 객체 직렬화 지원

            // 객체의 구체적인 클래스 타입 정보(메타데이터)를 JSON에 함께 박아두어 다운캐스팅 오류 방지
            activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
            )
        }

        val serializer = Jackson2JsonRedisSerializer(objectMapper, Any::class.java)

        // 기본 캐시 설정 규칙 정의
        val defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30)) // 기본 캐시 만료 시간: 30분
            .disableCachingNullValues() // null 값은 캐싱 제외
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer)
            )

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultCacheConfig)
            // 만약 특정 캐시 이름(예: "user-cache")만 TTL을 다르게 주고 싶다면 아래처럼 오버라이딩 가능
            .withCacheConfiguration(
                "user-cache",
                defaultCacheConfig.entryTtl(Duration.ofMinutes(15)) // 유저 캐시는 좀 더 신선하게 15분으로 설정 가능
            )
            .build()
    }
}