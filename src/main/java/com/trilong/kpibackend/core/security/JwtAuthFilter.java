package com.trilong.kpibackend.core.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthFilter — interceptor JWT chạy trước mỗi request.
 *
 * Luồng:
 * 1. Đọc "Authorization: Bearer <token>" từ header
 * 2. Validate token bằng JwtUtils
 * 3. Parse claims → tạo UserPrincipal (KHÔNG query DB)
 * 4. Set vào SecurityContextHolder để Spring Security nhận diện user
 * 5. Tiếp tục filter chain → SecurityFilterChain kiểm tra quyền
 *
 * Không query DB mỗi request → tốc độ tối ưu.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractBearerToken(request);

        // Chỉ xử lý nếu có token hợp lệ và chưa có authentication trong context
        if (token != null && jwtUtils.isTokenValid(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtUtils.extractAllClaims(token);

                // Tạo UserPrincipal từ claims — không cần query DB
                Number deptId = (Number) claims.get("departmentId");
                UserPrincipal principal = UserPrincipal.fromClaims(
                        Long.parseLong(claims.getSubject()),
                        claims.get("phoneNumber", String.class),
                        claims.get("fullName", String.class),
                        claims.get("role", String.class),
                        deptId != null ? deptId.longValue() : null,
                        claims.get("avatarUrl", String.class)
                );

                // Tạo authentication object và set vào SecurityContext
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception e) {
                // Token bị tampered hoặc lỗi parse — xóa context, để Security tự xử lý
                SecurityContextHolder.clearContext();
                logger.warn("JWT parse error: " + e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Trích xuất token từ header "Authorization: Bearer <token>"
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }
}
