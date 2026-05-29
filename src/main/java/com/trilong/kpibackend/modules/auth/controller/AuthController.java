package com.trilong.kpibackend.modules.auth.controller;

import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.modules.auth.dto.*;
import com.trilong.kpibackend.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AuthController — quản lý toàn bộ luồng xác thực.
 *
 * Base URL: /api/v1/auth
 *
 * Public endpoints (không cần token):
 *   POST /login    → Đăng nhập
 *   POST /refresh  → Lấy access token mới
 *   GET  /ping     → Kiểm tra server
 *
 * Protected endpoints (cần Bearer token):
 *   POST /logout          → Đăng xuất (revoke refresh token)
 *   GET  /me              → Xem thông tin hiện tại
 *   POST /change-password → Đổi mật khẩu
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Đăng nhập, làm mới token, đổi mật khẩu")
public class AuthController {

    @Autowired
    private AuthService authService;

    // ── POST /login ──────────────────────────────────────────────────────────

    @Operation(
            summary = "Đăng nhập",
            description = "Xác thực bằng số điện thoại và mật khẩu. " +
                    "Trả về access token (1h) và refresh token (7 ngày). " +
                    "Lưu cả 2 token vào secure storage của app."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công"),
            @ApiResponse(responseCode = "400", description = "Sai mật khẩu hoặc tài khoản bị khóa",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"status\":\"ERROR\",\"message\":\"Mật khẩu không chính xác\"}")))
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequestDTO request,
            HttpServletRequest httpRequest) {
        LoginResponseDTO response = authService.login(request, httpRequest);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", response));
    }

    // ── POST /refresh ────────────────────────────────────────────────────────

    @Operation(
            summary = "Làm mới Access Token",
            description = "Dùng refresh token để lấy access token mới mà không cần đăng nhập lại. " +
                    "Refresh token cũ bị thu hồi (rotation), nhận refresh token mới."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token mới được cấp"),
            @ApiResponse(responseCode = "400", description = "Refresh token hết hạn hoặc không hợp lệ")
    })
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshTokenRequestDTO request) {
        LoginResponseDTO response = authService.refreshToken(request);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", response));
    }

    // ── POST /logout ─────────────────────────────────────────────────────────

    @Operation(
            summary = "Đăng xuất",
            description = "Thu hồi refresh token. Access token hiện tại vẫn valid cho đến khi hết hạn (1h). " +
                    "App cần xóa cả 2 token khỏi local storage.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> logout(@Valid @RequestBody RefreshTokenRequestDTO request) {
        authService.logout(request);
        return ResponseEntity.ok(Map.of("status", "SUCCESS",
                "message", "Đăng xuất thành công. Vui lòng xóa token khỏi thiết bị."));
    }

    // ── GET /me ──────────────────────────────────────────────────────────────

    @Operation(
            summary = "Thông tin tài khoản hiện tại",
            description = "Lấy thông tin từ JWT token — không query database. Dùng để hiển thị profile.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getProfile(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        UserProfileDTO profile = authService.getProfile(currentUser);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", profile));
    }

    // ── PUT /avatar ──────────────────────────────────────────────────────────

    @Operation(
            summary = "Cập nhật ảnh chân dung gốc",
            description = "Cập nhật URL ảnh chân dung gốc (avatar) dùng cho xác thực khuôn mặt khi check-in.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật ảnh đại diện thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ")
    })
    @PutMapping("/avatar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateAvatar(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody UpdateAvatarRequestDTO request) {
        authService.updateAvatar(currentUser, request);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Cập nhật ảnh đại diện thành công"));
    }

    // ── POST /change-password ────────────────────────────────────────────────

    @Operation(
            summary = "Đổi mật khẩu",
            description = "Đổi mật khẩu. Sau khi đổi, TẤT CẢ phiên đăng nhập trên mọi thiết bị " +
                    "sẽ bị thu hồi — người dùng cần đăng nhập lại.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đổi mật khẩu thành công"),
            @ApiResponse(responseCode = "400", description = "Mật khẩu cũ sai hoặc xác nhận không khớp")
    })
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody ChangePasswordRequestDTO request) {
        authService.changePassword(currentUser, request);
        return ResponseEntity.ok(Map.of("status", "SUCCESS",
                "message", "Đổi mật khẩu thành công. Vui lòng đăng nhập lại trên tất cả thiết bị."));
    }

    // ── GET /ping ────────────────────────────────────────────────────────────
    @Operation(summary = "Kiểm tra server", description = "Health check — không cần token.")
    @GetMapping("/ping")
    public ResponseEntity<?> ping() {
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "message", "KPI System đang hoạt động",
                "version", "2.0.0"
        ));
    }

    // ── GET /check-status ────────────────────────────────────────────────────
    @Operation(summary = "Kiểm tra trạng thái mạng cho MobileApp", description = "Kiểm tra kết nối và trả về OK — không cần token.")
    @GetMapping("/check-status")
    public ResponseEntity<?> checkStatus() {
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "message", "Server is connected"
        ));
    }
}