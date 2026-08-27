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
    private final com.trilong.kpibackend.modules.user.repository.UserRepository userRepository;
    private final com.trilong.kpibackend.modules.notification.service.PushNotificationService pushNotificationService;

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

    @Operation(
            summary = "Tình trạng thông báo đẩy (Admin)",
            description = """
                    Cho biết thông báo đẩy đã chạy được chưa, không phải đoán mò:

                    - `pushConfigured` — máy chủ đã nạp khóa Firebase chưa. False nghĩa là
                      thiếu biến môi trường FIREBASE_SERVICE_ACCOUNT trên Render.
                    - `totalDevices` — bao nhiêu máy đã đăng ký nhận. Bằng 0 nghĩa là chưa
                      ai mở bản app mới, hoặc app chưa xin được quyền thông báo.

                    Có cả hai điều kiện thì thông báo đẩy hoạt động.
                    """
    )
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:manage')")
    public ResponseEntity<?> status() {
        var tatCa = deviceTokenRepository.findAll();
        long android = tatCa.stream().filter(d -> "android".equalsIgnoreCase(d.getPlatform())).count();
        long ios = tatCa.stream().filter(d -> "ios".equalsIgnoreCase(d.getPlatform())).count();

        // Vài máy đăng ký gần nhất — đủ để biết bản app mới đã tới tay ai chưa
        var ganDay = tatCa.stream()
                .filter(d -> d.getUpdatedAt() != null)
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .limit(10)
                .map(d -> Map.of(
                        "userId", d.getUserId(),
                        "userFullName", userRepository.findById(d.getUserId())
                                .map(u -> (Object) u.getFullName()).orElse("?"),
                        "platform", d.getPlatform() == null ? "?" : d.getPlatform(),
                        "updatedAt", d.getUpdatedAt().toString()))
                .toList();

        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", Map.of(
                "pushConfigured", pushNotificationService.daCauHinh(),
                "totalDevices", tatCa.size(),
                "android", android,
                "ios", ios,
                "recent", ganDay)));
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
