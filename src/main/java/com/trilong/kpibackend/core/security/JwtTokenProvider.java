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
import java.util.Map;

/**
 * JwtTokenProvider — lớp chuyên trách tạo và giải mã JWT token.
 *
 * Tách biệt với JwtUtils để tuân thủ Single Responsibility:
 * - JwtTokenProvider: logic tạo/decode token (nghiệp vụ)
 * - JwtUtils:         các hàm tiện ích trợ giúp (utility)
 *
 * Dùng thuật toán HMAC-SHA384 (HS384) — cân bằng giữa bảo mật và hiệu năng.
 */
@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    /**
     * Tạo token với đầy đủ claims cho user.
     * Claims được nhúng vào token để không cần query DB ở mỗi request.
     */
    public String createToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claims(Map.of(
                        "phoneNumber", user.getPhoneNumber(),
                        "fullName", user.getFullName(),
                        "role", user.getRole(),
                        "departmentId",
                        user.getDepartment() != null ? user.getDepartment().getId() : ""
                ))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Tạo refresh token — thời gian sống dài hơn (7 ngày).
     * Dùng để lấy access token mới mà không cần login lại.
     * (Sẽ implement đầy đủ ở phase 2)
     */
    public String createRefreshToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + (jwtExpirationMs * 7));

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("type", "REFRESH")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Giải mã token và trả về toàn bộ claims.
     * Throw exception nếu token không hợp lệ hoặc hết hạn.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Kiểm tra token hợp lệ và chưa hết hạn.
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Kiểm tra token có phải là Refresh Token không.
     */
    public boolean isRefreshToken(String token) {
        try {
            Claims claims = parseToken(token);
            return "REFRESH".equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
