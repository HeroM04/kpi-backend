package com.trilong.kpibackend.modules.notification.controller;

import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.modules.notification.entity.DeviceToken;
import com.trilong.kpibackend.modules.notification.repository.DeviceTokenRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Ứng dụng đăng ký mã thiết bị để nhận thông báo đẩy.
 */
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Tag(name = "Device Token", description = "Mã thiết bị để nhận thông báo đẩy")
@SecurityRequirement(name = "Bearer Authentication")
public class DeviceTokenController {

    private final DeviceTokenRepository deviceTokenRepository;

    @Operation(summary = "Đăng ký mã thiết bị của mình")
    @PostMapping("/register")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<?> register(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "Thiếu token."));
        }

        // Một mã chỉ thuộc về một người tại một thời điểm: người khác đăng nhập
        // trên cùng máy thì mã chuyển sang họ, tránh gửi nhầm cho chủ cũ.
        DeviceToken dt = deviceTokenRepository.findByToken(token).orElseGet(DeviceToken::new);
        dt.setToken(token);
        dt.setUserId(currentUser.getUserId());
        dt.setPlatform(body.getOrDefault("platform", null));
        deviceTokenRepository.save(dt);

        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    @Operation(summary = "Gỡ mã thiết bị (khi đăng xuất)")
    @DeleteMapping("/register")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<?> unregister(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token != null && !token.isBlank()) {
            deviceTokenRepository.deleteByToken(token);
        }
        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }
}
