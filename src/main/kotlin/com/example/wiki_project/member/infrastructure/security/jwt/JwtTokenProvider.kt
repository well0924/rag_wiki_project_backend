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

    // 락 프리(Lock-free) 가독성을 위해 lazy를 사용하여 시크릿 키 객체를 최초 1회 초기화
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
            .claim("role", userDetails.member.role) // Enum 객체 대신 명확하게 문자열(.name) 형태로 주입
            .claim("groupIds", userDetails.groupIds) // RAG 데이터 필터링용 소속 그룹 ID 리스트
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

    /**
     * [인증 객체(Authentication) 추출]
     * JWT 토큰 페이로드를 뜯어 스프링 시큐리티 컨텍스트에 등록할 UsernamePasswordAuthenticationToken을 빌드합니다.
     * * 중요 널 방어 분기 설계:
     * - Refresh Token 재발급(reissue) 시에도 이 메서드가 공유되어 필터에서 호출됩니다.
     * - Refresh Token은 'role'과 'groupIds' 클레임이 없어 기존 자바 라이브러리 연동 시 코틀린 단에서
     * "null cannot be cast to non-null type String" (500 에러) 가 터졌습니다.
     * - 이를 막기 위해 클레임 존재 여부에 따라 이중 분기(Early Return 효과) 구조로 안전하게 리팩토링했습니다.
     */
    fun getAuthentication(token: String): Authentication {
        val claims = parseClaims(token)
        val email = claims.subject ?: throw io.jsonwebtoken.JwtException("토큰에 subject가 없습니다.")

        // 토큰 타입 판별 (role 클레임 존재 여부로 판단)
        val roleHeader = claims["role"]

        // 코틀린의 'if' 식을 활용하여 변수 오염 없이 구조 분해 할당
        val (currentRole, groupIds) = if (roleHeader == null) {
            // 클레임이 없으므로 안전하게 기본값 할당
            Pair(Role.ROLE_MEMBER, emptyList<Long>())
        } else {
            // 정상적인 권한 및 groupIds 파싱 진행
            val parsedRole = runCatching { Role.valueOf(roleHeader.toString()) }.getOrDefault(Role.ROLE_MEMBER)
            val parsedGroupIds = parseGroupIds(claims["groupIds"])
            Pair(parsedRole, parsedGroupIds)
        }

        // UserDetails 및 인증 객체 빌드
        val userDetails = createCustomUserDetails(email, currentRole, groupIds)
        return UsernamePasswordAuthenticationToken(userDetails, token, userDetails.authorities)
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

    /**
     * groupIds 클레임을 안전하게 Long 리스트로 변환하는 비공개 메서드
     */
    private fun parseGroupIds(groupIdsRaw: Any?): List<Long> {
        val rawList = groupIdsRaw as? List<*> ?: return emptyList()
        return rawList.mapNotNull { item ->
            when (item) {
                is Number -> item.toLong()
                is String -> item.toLongOrNull()
                else -> null
            }
        }
    }

    /**
     * CustomUserDetails 생성을 담당하는 팩토리 메서드
     */
    private fun createCustomUserDetails(email: String, role: Role, groupIds: List<Long>): CustomUserDetails {
        return CustomUserDetails(
            member = MemberModel(
                email = email,
                password = "", // 시큐리티 인증용 더미 데이터
                userName = null,
                role = role
            ),
            groupIds = groupIds,
            isAdmin = (role == Role.ROLE_ADMIN)
        )
    }

}