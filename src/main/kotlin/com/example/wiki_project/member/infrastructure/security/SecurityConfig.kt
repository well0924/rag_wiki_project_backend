package com.example.wiki_project.member.infrastructure.security

import com.example.wiki_project.member.infrastructure.security.auth.CustomAuthenticationManager
import com.example.wiki_project.member.infrastructure.security.filter.JwtAuthenticationFilter
import com.example.wiki_project.member.infrastructure.security.filter.InternalApiKeyFilter
import com.example.wiki_project.member.infrastructure.security.filter.JwtExceptionFilter
import com.example.wiki_project.member.infrastructure.security.handler.CustomAccessDeniedHandler
import com.example.wiki_project.member.infrastructure.security.handler.CustomAuthenticationEntryPoint
import com.example.wiki_project.member.infrastructure.security.handler.OAuth2FailureHandler
import com.example.wiki_project.member.infrastructure.security.handler.OAuth2SuccessHandler
import com.example.wiki_project.member.infrastructure.security.oauth.CustomOAuth2UserService
import com.example.wiki_project.member.infrastructure.security.oauth.GoogleOAuth2AuthenticationProvider
import jakarta.servlet.DispatcherType
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource


@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val jwtExceptionFilter: JwtExceptionFilter,
    private val internalApiKeyFilter: InternalApiKeyFilter,
    private val customAuthenticationEntryPoint: CustomAuthenticationEntryPoint,
    private val customAccessDeniedHandler: CustomAccessDeniedHandler,
    private val customOAuth2UserService: CustomOAuth2UserService, // 구글 스펙 파싱 서비스 주입
    private val oAuth2SuccessHandler: OAuth2SuccessHandler,       // 성공 마스터 핸들러 주입
    private val oAuth2FailureHandler: OAuth2FailureHandler,
    // 콤마 구분 (예: https://wiki.example.com,http://localhost:3000)
    @Value("\${app.cors.allowed-origins:http://localhost:3000}") private val allowedOrigins: List<String>
) {

    @Bean
    fun authenticationManager(
        customAuthenticationManager: CustomAuthenticationManager,
        googleOAuth2AuthenticationProvider: GoogleOAuth2AuthenticationProvider
    ): AuthenticationManager {
        return customAuthenticationManager
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // CSRF 및 Session 비활성화 (Rest API + JWT 환경 정석)
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) } // cors 추가
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // HTTP 요청별 권한 세분화 구성
            .authorizeHttpRequests { auth ->
                auth
                    // suspend 컨트롤러는 비동기 디스패치(ASYNC)로 응답을 쓰는데, STATELESS라 이때 SecurityContext가 비어 있음
                    // → 최초 REQUEST 디스패치에서 이미 인가를 통과했으므로 ASYNC / ERROR 디스패치는 허용 (미설정 시 처리 후 401 응답)
                    .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                    .requestMatchers("/api/v1/auth/**").permitAll() // 로그인, 회원가입 허용
                    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN") // 관리자 영역 격리
                    .requestMatchers("/api/v1/internal/**").hasRole("INTERNAL") // AI 서버 전용 내부 API (공유 키 인증)
                    .anyRequest().authenticated() // 나머지는 전부 인증 필요
            }
            // 커스텀 JWT 검증 필터 배치 (UsernamePasswordAuthenticationFilter 직전에 실행)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtExceptionFilter, JwtAuthenticationFilter::class.java)
            .addFilterBefore(internalApiKeyFilter, JwtAuthenticationFilter::class.java)
            .oauth2Login { oauth2 ->
                oauth2
                    .userInfoEndpoint { userInfo ->
                        userInfo.userService(customOAuth2UserService)
                    }
                    .successHandler(oAuth2SuccessHandler)
                    .failureHandler(oAuth2FailureHandler)
            }
            // 필터 레이어 예외 처리(Exception Handling) 꼼꼼하게 매핑
            .exceptionHandling {
                exception ->
                exception
                    .authenticationEntryPoint(customAuthenticationEntryPoint) // 401
                    .accessDeniedHandler(customAccessDeniedHandler) // 403
            }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = allowedOrigins
        // 허용할 HTTP 메서드
        configuration.allowedMethods =
            listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
        // 허용할 헤더
        configuration.allowedHeaders = listOf("Authorization", "Cache-Control", "Content-Type")
        // 자격 증명 허용 (쿠키, 인증 헤더 등을 포함할 경우 필수)
        configuration.allowCredentials = true
        // 노출할 헤더 (필요시 추가)
        configuration.exposedHeaders = listOf("Authorization")
        val source: UrlBasedCorsConfigurationSource = UrlBasedCorsConfigurationSource()
        // 모든 엔드포인트에 위 CORS 설정 적용
        source.registerCorsConfiguration("/**", configuration)

        return source
    }
}