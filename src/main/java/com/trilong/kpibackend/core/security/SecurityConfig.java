package com.trilong.kpibackend.core.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired private UserDetailsServiceImpl userDetailsService;
    @Autowired private JwtAuthFilter jwtAuthFilter;
    @Autowired private CustomAuthEntryPoint authEntryPoint;
    @Autowired private CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                .authorizeHttpRequests(auth -> auth

                        // 1. PUBLIC — Không cần token (THÊM /ws-feedbacks VÀO ĐÂY)
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/ping",
                                "/api/v1/auth/check-status",
                                "/ws/**", // <--- QUAN TRỌNG: Cho phép SockJS handshake
                                "/ws-feedbacks/**" // <--- QUAN TRỌNG: Mở cổng cho WebSocket handshake
                        ).permitAll()

                        // 2. Swagger UI & API Docs
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml"
                        ).permitAll()

                        // 3. Serve ảnh upload local (public)
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()

                        // ================== NGHIỆP VỤ HỆ THỐNG (PHÂN QUYỀN QUA @PreAuthorize Ở CONTROLLER) ==================
                        .requestMatchers("/api/v1/feedbacks/**").authenticated()
                        .requestMatchers("/api/v1/deals/**").authenticated()
                        .requestMatchers("/api/v1/social-posts/**").authenticated()
                        .requestMatchers("/api/v1/field-battle/**").authenticated()
                        .requestMatchers("/api/v1/training-sessions/**").authenticated()
                        .requestMatchers("/api/v1/kpi-scores/**").authenticated()
                        // ====================================================================================

                        // 4. ADMIN only
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // 5. ADMIN + Trưởng phòng
                        .requestMatchers("/api/v1/manager/**").hasAnyRole("ADMIN", "TRUONG_PHONG")

                        // 6. Payroll endpoints (fine-grained control via @PreAuthorize)
                        .requestMatchers("/api/v1/payroll/**").authenticated()

                        // 7. Tất cả các endpoint còn lại bắt buộc phải có token
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}