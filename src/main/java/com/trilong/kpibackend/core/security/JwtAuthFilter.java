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
 * 3. Parse claims → tạo UserPrincipal
 * 4. Đè vai trò / phòng ban / tên / ảnh bằng bản chụp DB ({@link HoSoNongService},
 *    giữ 60 giây, quên ngay khi Admin sửa) — token chỉ còn là bằng chứng
 *    "đây là ai", không phải "người này đang ở phòng nào". Tài khoản không còn
 *    ACTIVE thì không cấp quyền, dù token vẫn hạn.
 * 5. Set vào SecurityContextHolder để Spring Security nhận diện user
 * 6. Tiếp tục filter chain → SecurityFilterChain kiểm tra quyền
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private HoSoNongService hoSoNong;

    @Autowired
    private com.trilong.kpibackend.modules.auth.service.PhienDangNhapService phienDangNhap;

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

                Long userId = Long.parseLong(claims.getSubject());
                Number deptId = (Number) claims.get("departmentId");
                String role = claims.get("role", String.class);
                Long departmentId = deptId != null ? deptId.longValue() : null;
                String fullName = claims.get("fullName", String.class);
                String avatarUrl = claims.get("avatarUrl", String.class);

                Number sidClaim = (Number) claims.get("sid");
                Long sid = sidClaim != null ? sidClaim.longValue() : null;

                // DB nói gì thì theo đó; DB không trả lời thì dùng tạm token.
                var banChup = hoSoNong.lay(userId);
                if (banChup.isPresent()) {
                    var b = banChup.get();
                    if (!b.dangHoatDong()) {
                        logger.info("Từ chối token của nhân sự " + userId + " (trạng thái " + b.status() + ")");
                        filterChain.doFilter(request, response);
                        return;
                    }
                    // Đã bấm "đăng xuất mọi thiết bị", hoặc riêng máy này bị gỡ.
                    // Chặn ngay tại đây chứ không đợi token hết hạn.
                    if (b.tokenQuaCu(claims.getIssuedAt()) || b.phienBiThuHoi(sid)) {
                        logger.info("Từ chối token cũ của nhân sự " + userId + " (phiên " + sid + ")");
                        filterChain.doFilter(request, response);
                        return;
                    }
                    role = b.role();
                    departmentId = b.departmentId();
                    fullName = b.fullName();
                    avatarUrl = b.avatarUrl();
                }

                UserPrincipal principal = UserPrincipal.fromClaims(
                        userId,
                        claims.get("phoneNumber", String.class),
                        fullName, role, departmentId, avatarUrl
                );
                principal.setSessionId(sid);
                phienDangNhap.ghiNhanHoatDong(sid);   // chạy nền, tối đa 1 lần/phút mỗi phiên

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
