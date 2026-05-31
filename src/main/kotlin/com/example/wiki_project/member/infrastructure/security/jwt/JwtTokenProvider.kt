package com.example.wiki_project.member.infrastructure.security.jwt

import com.example.wiki_project.member.domain.model.MemberModel
import com.example.wiki_project.member.domain.model.Role
import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetails
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
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

    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(secretKeyString.toByteArray())
    }

    /**
     * Access Token 생성 (RAG 필터링용 groupIds 포함)
     */
    fun createAccessToken(authentication: Authentication): String {
        val userDetails = authentication.principal as CustomUserDetails
        val now = Date()
        return Jwts.builder()
            .subject(userDetails.username)
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
            .issuedAt(now)
            .expiration(Date(now.time + refreshExpiration))
            .signWith(secretKey)
            .compact()
    }

    fun getAuthentication(token: String): Authentication {
        val claims = parseClaims(token)
        val email = claims.subject
        val role = claims["role"] as String
        val currentRole = Role.valueOf(role)
        // groupIds 캐스팅 (Int ➔ Long)
        val groupIdsRaw = claims["groupIds"] as? List<*> ?: emptyList<Any>()
        val groupIds = groupIdsRaw.map { (it as Number).toLong() }
        val isAdmin = currentRole == Role.ROLE_ADMIN

        val userDetails = CustomUserDetails(
            MemberModel(
                email = email,
                password = "",
                userName = null,
                role = currentRole
            ),
            groupIds = groupIds,
            isAdmin = isAdmin
        )
        return UsernamePasswordAuthenticationToken(userDetails, token, userDetails.authorities)
    }

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

    // 블랙리스트 대조 시 토큰의 남은 만료 시간(TTL)을 계산해 주기 위한 메서드
    fun getExpiration(token: String): Long {
        return try {
            val expiration = parseClaims(token).expiration
            expiration.time - Date().time
        } catch (e: ExpiredJwtException) {
            // 이미 만료된 토큰 → 블랙리스트 등록 불필요, 0 반환
            0L
        }
    }

    private fun parseClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}