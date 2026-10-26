package com.example.wiki_project.member.infrastructure.security.jwt

import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetails
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}") private val secretKeyString: String,
    @Value("\${jwt.access-expiration}") private val accessExpiration: Long, // Access 토큰 시간 (30분)
    @Value("\${jwt.refresh-expiration}") private val refreshExpiration: Long // Refresh 토큰 시간 (14일)
) {

    companion object {
        private const val TYPE_CLAIM = "type"
        private const val ACCESS_TYPE = "access"
        private const val REFRESH_TYPE = "refresh"
    }

    // 락 프리(Lock-free) 가독성을 위해 lazy를 사용하여 시크릿 키 객체를 최초 1회 초기화
    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(secretKeyString.toByteArray())
    }

    /**
     * Access Token 생성
     * - role / groupIds 클레임은 참고용이며, 실제 인가는 필터에서 Redis 캐시(미스 시 DB)의 최신 값으로 수행
     */
    fun createAccessToken(authentication: Authentication): String {
        val userDetails = authentication.principal as CustomUserDetails
        val now = Date()
        return Jwts.builder()
            .subject(userDetails.username)
            .claim(TYPE_CLAIM, ACCESS_TYPE)
            .claim("role", userDetails.member.role.name)
            .claim("groupIds", userDetails.groupIds)
            .issuedAt(now)
            .expiration(Date(now.time + accessExpiration))
            .signWith(secretKey)
            .compact()
    }

    /**
     * Refresh Token 생성 (서버 비용 절감을 위해 가볍게 subject만 탑재)
     */
    fun createRefreshToken(email: String): String {
        val now = Date()
        return Jwts.builder()
            .subject(email)
            .claim(TYPE_CLAIM, REFRESH_TYPE)
            .issuedAt(now)
            .expiration(Date(now.time + refreshExpiration))
            .signWith(secretKey)
            .compact()
    }

    /**
     * 토큰 subject(이메일) 추출
     */
    fun getEmail(token: String): String {
        return parseClaims(token).subject ?: throw io.jsonwebtoken.JwtException("토큰에 subject가 없습니다.")
    }

    /**
     * API 인증에 사용할 수 있는 Access Token인지 판별
     * - type 클레임 도입 이전에 발급된 토큰은 role 클레임 존재 여부로 판별
     */
    fun isAccessToken(token: String): Boolean {
        val claims = parseClaims(token)
        val type = claims[TYPE_CLAIM] ?: return claims["role"] != null
        return type == ACCESS_TYPE
    }

    /**
     * 재발급(reissue)에 사용할 수 있는 Refresh Token인지 판별
     */
    fun isRefreshToken(token: String): Boolean {
        val claims = parseClaims(token)
        val type = claims[TYPE_CLAIM] ?: return claims["role"] == null
        return type == REFRESH_TYPE
    }

    /**
     * 토큰 기본 유효성 및 서명 검증
     */
    fun validateToken(token: String): Boolean {
        return try {
            parseClaims(token)
            true
        } catch (e: Exception) {
            // ExpiredJwtException, SignatureException 등을 로깅하거나
            // filter 단에서 예외를 세밀하게 핸들링하고 싶다면 여기서 catch 후 custom exception으로 throw
            false
        }
    }

    /**
     * 토큰 잔여 유효 시간(TTL) 계산
     */
    fun getExpiration(token: String): Long {
        return try {
            val expiration = parseClaims(token).expiration
            expiration.time - Date().time
        } catch (e: ExpiredJwtException) {
            // 이미 만료된 토큰 → 블랙리스트 등록 불필요, 0 반환
            0L
        }
    }

    /**
     * JWT 클레임 복호화/파싱
     */
    private fun parseClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
