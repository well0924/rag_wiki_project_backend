# RAG 기반 사내 위키 검색 에이전트 (Backend)

> Kotlin · Spring Boot · Spring Security · JWT · Redis · PostgreSQL · Coroutines · Redis Streams
 
---

## 📌 프로젝트 개요

사내 문서를 업로드하면 Python FastAPI RAG 엔진이 벡터 인덱싱하고,  
임직원이 자연어로 질의하면 AI가 소속 부서 권한 기반으로 필터링된 답변을 반환하는 사내 위키 검색 시스템입니다.
 
---

## 🛠️ 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Kotlin |
| Framework | Spring Boot 3.x |
| Security | Spring Security + JWT + Redis Blacklist |
| DB | PostgreSQL |
| Cache / MQ | Redis (캐시, Refresh Token, Blacklist, Streams) |
| Async | Kotlin Coroutines |
| ORM | Spring Data JPA (Hibernate 6) |
| Test | JUnit5 · Mockito-Kotlin |
 
---

## 🏗️ 전체 아키텍처

```
[클라이언트]
    │
    ▼
[Spring Boot Backend] ──── Redis (캐시 / 토큰 / 블랙리스트 / Streams)
    │                            │
    │                            ▼
    ├─── PostgreSQL         [Python FastAPI RAG Worker]
    │                            │
    └─────────────────────────── 벡터 DB (문서 임베딩 인덱싱)
```
 
---

## 📁 패키지 구조

```
wiki_project/
├── common/                   ← 공통 예외, 에러코드
├── member/
│   ├── application/          ← MemberService, AdminService, AuthService
│   ├── domain/               ← MemberModel(data class), Entity, Mapper
│   └── infrastructure/
│       ├── security/         ← JWT, Filter, Provider, OAuth2
│       └── repository/
├── document/
│   ├── application/          ← DocumentService (코루틴 + Redis Streams)
│   └── domain/
└── searchhistory/
    ├── application/          ← SearchUsecase (RAG 파이프라인 파사드)
    └── domain/
```
 
---

## 🔐 인증 시스템

### 설계 포인트

Spring Security를 기본 설정 없이 전부 커스텀했습니다.  
라이브러리 내부(`ProviderManager`, `AuthenticationProvider`) 동작 방식을 직접 분석하고 아래 구조를 설계했습니다.

```
요청
 │
 ├─► JwtExceptionFilter (JWT 예외 → JSON 응답 변환)
 ├─► JwtAuthenticationFilter (토큰 검증 + Redis 블랙리스트 조회)
 └─► CustomAuthenticationManager
         ├─► CustomAuthenticationProvider      (일반 ID/PW 로그인)
         └─► GoogleOAuth2AuthenticationProvider (소셜 로그인 + JIT 가입)
```

### 주요 전략

| 전략 | 내용 |
|---|---|
| **JWT Blacklist** | 로그아웃/탈퇴 시 Access Token을 Redis에 TTL로 등록하여 재사용 차단 |
| **RTR** | 재발급 시 구 Refresh Token 교체. 불일치 시 탈취 의심으로 토큰/캐시 즉시 파기 |
| **Redis 캐시** | `UserCacheData(MemberModel + groupIds + isAdmin)` 통합 캐싱으로 매 요청 DB I/O 방지 |
| **Soft Delete 가드** | 일반/소셜 로그인 양쪽 Provider에서 동일한 탈퇴 계정 차단 로직 적용 |
 
---

## 📄 문서 업로드 파이프라인

```
컨트롤러 (동기 영역 - 인증/검증 완료)
    │
    ▼ withContext(Dispatchers.IO) ← 여기서부터 비동기
    │
    ├─► RDB 저장 (문서 메타데이터 + 그룹 권한 매핑)
    ├─► Redis Streams XADD (document:upload:stream)
    └─► 즉시 응답 반환
              │
              ▼ (비동기)
         Python RAG Worker
              │
              ▼
         벡터 인덱싱 완료
```

Spring MVC 위에서 코루틴을 사용할 때 `ThreadLocal` 기반의 SecurityContext가 스레드 전환 시 유실되는 문제를,  
**인증 완료 후 불변 Command 객체만 비동기 영역으로 전달**하는 방식으로 해결했습니다.
 
---

## 🗃️ 주요 설계 결정

### 1. UserGroupMemberId 복합키 인덱스 최적화

```kotlin
class UserGroupMemberId(
    var member: Long? = null,  // 선두 컬럼 → user_id 단일 조건 조회 시 PK 인덱스 활용
    var group: Long? = null
) : Serializable
```

`member(user_id)`를 복합 PK의 선두 컬럼으로 배치하여 PostgreSQL B-Tree 인덱스의  
**Index Only Scan**을 유도했습니다. 별도 인덱스 없이 `user_id` 단일 조건 조회가 가능합니다.

### 2. MemberModel data class 전환

초기 설계에서 `class`로 작성된 도메인 모델이 `copy()` 미사용으로 인해  
상태 변경 메서드마다 `isDeleted` 등 필드가 누락되는 버그가 잠재되어 있었습니다.

`data class`로 전환하여 `copy()`를 통한 불변 복사본 반환으로 리팩터링했습니다.

```kotlin
// 변경 전 - 필드 누락 위험
fun withdraw(): MemberModel {
    return MemberModel(id, email, password, userName, role, createdAt, LocalDateTime.now())
    // isDeleted 누락 → Soft Delete 미작동
}
 
// 변경 후
fun withdraw(): MemberModel {
    check(!this.isDeleted) { "이미 탈퇴한 회원입니다." }
    return copy(isDeleted = true, updatedAt = LocalDateTime.now())
}
```

### 3. Redis 캐시 구조 개선

초기에 `MemberModel`만 캐싱하여 캐시 히트 시에도 `groupIds` 조회를 위해 DB를 재조회하는 문제가 있었습니다.

`UserCacheData`로 통합하여 캐시 히트 시 DB 조회를 완전히 우회하도록 개선했습니다.

```kotlin
data class UserCacheData(
    val member: MemberModel,
    val groupIds: List<Long>,
    val isAdmin: Boolean
)
```
 
---

## 🔥 트러블슈팅

### Redis CLI 한글 깨짐 (Windows PowerShell)

Windows PowerShell 기본 인코딩(CP949)과 Python Worker의 UTF-8 디코딩 불일치로 한글이 깨지는 현상 발생.

```powershell
chcp 65001  # UTF-8로 전환 후 redis-cli 실행
```

Worker에 인코딩 Fallback도 추가하여 운영 환경과 무관하게 방어 처리했습니다.

### Mockito + Kotlin Non-Null NPE

`any()`가 null을 반환하여 Kotlin non-null 타입에서 NPE 발생.  
`mockito-kotlin` 라이브러리로 교체하여 해결했습니다.

### 코루틴 suspend 함수 단위 테스트

AssertJ의 `assertThatThrownBy` 람다 안에서 `suspend` 함수 호출 불가 문제.  
`org.junit.jupiter.api.assertThrows`로 전환하여 해결했습니다.
 
---

## ✅ 테스트

| 테스트 | 내용 |
|---|---|
| `MemberModelTest` | 도메인 비즈니스 규칙 단위 테스트 |
| `AdminServiceTest` | 권한 승격, 그룹 지정, 강제 추방 |
| `AuthServiceTest` | 로그인, 토큰 재발급, 탈취 의심 강제 파기 |
| `CustomUserDetailsServiceTest` | 캐시 히트 시 DB 미조회 검증 |
| `UserGroupMemberRepositoryTest` | 복합키 저장 및 인덱스 조회 |
 
---

## 🔗 관련 저장소

- Python FastAPI RAG 서버: (링크 추가 예정)
