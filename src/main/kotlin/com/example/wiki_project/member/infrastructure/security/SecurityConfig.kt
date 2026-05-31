package com.example.wiki_project.member.infrastructure.security

import com.example.wiki_project.member.infrastructure.security.auth.CustomAuthenticationManager
import com.example.wiki_project.member.infrastructure.security.filter.JwtAuthenticationFilter
import com.example.wiki_project.member.infrastructure.security.filter.JwtExceptionFilter
import com.example.wiki_project.member.infrastructure.security.handler.CustomAccessDeniedHandler
import com.example.wiki_project.member.infrastructure.security.handler.CustomAuthenticationEntryPoint
import com.example.wiki_project.member.infrastructure.security.oauth.CustomOAuth2UserService
import com.example.wiki_project.member.infrastructure.security.handler.OAuth2FailureHandler
import com.example.wiki_project.member.infrastructure.security.handler.OAuth2SuccessHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val jwtExceptionFilter: JwtExceptionFilter,
    private val customAuthenticationEntryPoint: CustomAuthenticationEntryPoint,
    private val customAccessDeniedHandler: CustomAccessDeniedHandler,
    private val customOAuth2UserService: CustomOAuth2UserService, // 구글 스펙 파싱 서비스 주입
    private val oAuth2SuccessHandler: OAuth2SuccessHandler,       // 성공 마스터 핸들러 주입
    private val oAuth2FailureHandler: OAuth2FailureHandler
) {

    @Bean
    fun authenticationManager(
        customAuthenticationManager: CustomAuthenticationManager
    ): AuthenticationManager {
        return customAuthenticationManager
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // CSRF 및 Session 비활성화 (Rest API + JWT 환경 정석)
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }

            // HTTP 요청별 권한 세분화 구성
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/v1/auth/**").permitAll() // 로그인, 회원가입 허용
                    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN") // 관리자 영역 격리
                    .anyRequest().authenticated() // 나머지는 전부 인증 필요
            }

            // 커스텀 JWT 검증 필터 배치 (UsernamePasswordAuthenticationFilter 직전에 실행)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtExceptionFilter, JwtAuthenticationFilter::class.java)
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
}