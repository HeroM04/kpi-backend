package com.trilong.kpibackend.modules.auth.service;

import com.trilong.kpibackend.core.security.AppPermission;
import com.trilong.kpibackend.core.security.JwtUtils;
import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.modules.auth.dto.*;
import com.trilong.kpibackend.modules.auth.entity.RefreshToken;
import com.trilong.kpibackend.modules.auth.repository.RefreshTokenRepository;
import com.trilong.kpibackend.modules.user.entity.Department;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * AuthService Phase 2 — xử lý toàn bộ luồng xác thực.
 *
 * Endpoints được hỗ trợ:
 * - login()          → access token + refresh token
 * - refresh()        → access token mới từ refresh token
 * - logout()         → revoke refresh token
 * - changePassword() → đổi mật khẩu + revoke tất cả phiên
 * - getProfile()     → thông tin từ token (không query DB)
 */
@Service
public class AuthService {

    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JwtUtils jwtUtils;

    @Value("${app.jwt.expiration-ms:3600000}")
    private long accessTokenExpMs;

    @Value("${app.jwt.refresh-expiration-ms:604800000}")
    private long refreshTokenExpMs;

    // ── Login ────────────────────────────────────────────────────────────────

    @Transactional
    public LoginResponseDTO login(LoginRequestDTO request, HttpServletRequest httpRequest) {
        // 1. Tìm user
        User user = userRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseThrow(() -> new RuntimeException(
                        "Không tìm thấy tài khoản với số điện thoại này"));

        // 2. Kiểm tra trạng thái tài khoản
        if (!"ACTIVE".equals(user.getStatus()))
            throw new RuntimeException("Tài khoản đã bị khóa. Liên hệ Admin để mở lại.");

        // 3. Verify mật khẩu BCrypt
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
            throw new RuntimeException("Mật khẩu không chính xác");

        // 4. Tạo access token JWT
        String accessToken = jwtUtils.generateToken(user);

        // 5. Tạo refresh token UUID và lưu DB
        String rawRefreshToken = UUID.randomUUID().toString();
        String deviceInfo = httpRequest.getHeader("User-Agent");
        String ipAddress  = getClientIp(httpRequest);

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token(rawRefreshToken)
                .expiresAt(ZonedDateTime.now().plusSeconds(refreshTokenExpMs / 1000))
                .deviceInfo(deviceInfo != null ? deviceInfo.substring(0, Math.min(deviceInfo.length(), 200)) : "Unknown")
                .ipAddress(ipAddress)
                .build());

        // 6. Build response
        Department dept = user.getDepartment();
        return LoginResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpMs / 1000)
                .userId(user.getId())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .status(user.getStatus())
                .permissions(AppPermission.getPermissionsByRole(user.getRole()).stream().map(AppPermission::getValue).toList())
                .avatarUrl(user.getAvatarUrl())
                .departmentId(dept != null ? dept.getId() : null)
                .departmentName(dept != null ? dept.getName() : null)
                .officeLat(dept != null ? dept.getOfficeLat() : null)
                .officeLng(dept != null ? dept.getOfficeLng() : null)
                .allowedRadius(dept != null ? dept.getAllowedRadius() : 50)
                .build();
    }

    // ── Refresh Token ────────────────────────────────────────────────────────

    @Transactional
    public LoginResponseDTO refreshToken(RefreshTokenRequestDTO request) {
        RefreshToken rt = refreshTokenRepository
                .findByToken(request.getRefreshToken())
                .orElseThrow(() -> new RuntimeException("Refresh token không hợp lệ"));

        if (!rt.isValid())
            throw new RuntimeException("Refresh token đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.");

        User user = rt.getUser();

        // Tạo access token mới
        String newAccessToken = jwtUtils.generateToken(user);

        // Rotate refresh token (best practice — revoke cũ, tạo mới)
        rt.setRevoked(true);
        refreshTokenRepository.save(rt);

        String newRawRefreshToken = UUID.randomUUID().toString();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token(newRawRefreshToken)
                .expiresAt(ZonedDateTime.now().plusSeconds(refreshTokenExpMs / 1000))
                .deviceInfo(rt.getDeviceInfo())
                .ipAddress(rt.getIpAddress())
                .build());

        Department dept = user.getDepartment();
        return LoginResponseDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpMs / 1000)
                .userId(user.getId())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .status(user.getStatus())
                .permissions(AppPermission.getPermissionsByRole(user.getRole()).stream().map(AppPermission::getValue).toList())
                .avatarUrl(user.getAvatarUrl())
                .departmentId(dept != null ? dept.getId() : null)
                .departmentName(dept != null ? dept.getName() : null)
                .officeLat(dept != null ? dept.getOfficeLat() : null)
                .officeLng(dept != null ? dept.getOfficeLng() : null)
                .allowedRadius(dept != null ? dept.getAllowedRadius() : 50)
                .build();
    }

    // ── Logout ───────────────────────────────────────────────────────────────

    @Transactional
    public void logout(RefreshTokenRequestDTO request) {
        refreshTokenRepository.findByToken(request.getRefreshToken())
                .ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepository.save(rt);
                });
        // Không throw error nếu token không tồn tại — logout luôn thành công về mặt UX
    }

    // ── Đổi mật khẩu ────────────────────────────────────────────────────────

    @Transactional
    public void changePassword(UserPrincipal currentUser, ChangePasswordRequestDTO request) {
        // Validate confirm password
        if (!request.getNewPassword().equals(request.getConfirmPassword()))
            throw new RuntimeException("Mật khẩu xác nhận không khớp");

        User user = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản"));

        // Verify mật khẩu cũ
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash()))
            throw new RuntimeException("Mật khẩu cũ không đúng");

        // Hash mật khẩu mới
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Revoke TẤT CẢ refresh token để buộc đăng nhập lại trên mọi thiết bị
        refreshTokenRepository.revokeAllByUser(user);
    }

    // ── Profile ──────────────────────────────────────────────────────────────

    /** Lấy profile từ token — không query DB */
    public UserProfileDTO getProfile(UserPrincipal currentUser) {
        return UserProfileDTO.from(currentUser);
    }

    // ── Cập nhật Avatar ──────────────────────────────────────────────────────
    @Transactional
    public void updateAvatar(UserPrincipal currentUser, UpdateAvatarRequestDTO request) {
        User user = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản"));
        user.setAvatarUrl(request.getAvatarUrl());
        userRepository.save(user);
        currentUser.setAvatarUrl(request.getAvatarUrl());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim(); // Lấy IP đầu tiên trong chain proxy
        }
        return request.getRemoteAddr();
    }
}
