package com.runmap.api.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 임시 보안 설정: 모든 요청을 permitAll로 연다.
 * 인증(JWT 등)을 도입할 때 anyRequest().permitAll()을 실제 인가 규칙으로 교체해야 한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)        // 토큰 기반 무상태 API라 CSRF 비활성화
                .httpBasic(AbstractHttpConfigurer::disable)   // 기본 Basic 인증 프롬프트 제거
                .formLogin(AbstractHttpConfigurer::disable)   // 기본 로그인 폼 제거
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll());
        return http.build();
    }
}
