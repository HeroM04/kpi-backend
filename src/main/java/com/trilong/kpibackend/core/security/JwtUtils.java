package com.trilong.kpibackend.core.security;

import com.trilong.kpibackend.modules.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Utility class để tạo và xác thực JWT Token.
 * Token chứa: userId, phoneNumber, role, fullName
 */
@Component
public class JwtUtils {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    /**
     * Tạo JWT token sau khi login thành công
     */
    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("phoneNumber", user.getPhoneNumber())
                .claim("fullName", user.getFullName())
                .claim("role", user.getRole())
                .claim("avatarUrl", user.getAvatarUrl())
                .claim("departmentId",
                        user.getDepartment() != null ? user.getDepartment().getId() : null)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSignKey())
                .compact();
    }

    /**
     * Giải mã token và lấy tất cả claims (userId, role,...)
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Lấy userId (subject) từ token
     */
    public Long extractUserId(String token) {
        return Long.parseLong(extractAllClaims(token).getSubject());
    }

    /**
     * Lấy role từ token
     */
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    /**
     * Kiểm tra token có hợp lệ và chưa hết hạn không
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private SecretKey getSignKey() {
        // Dùng raw bytes của secret (phải >= 32 ký tự để đủ độ dài HS256)
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
