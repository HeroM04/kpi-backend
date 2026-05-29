package com.trilong.kpibackend.modules.notification.controller;

import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.modules.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Hệ thống thông báo đẩy cho Mobile App và Web Admin")
@SecurityRequirement(name = "Bearer Authentication")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Lấy danh sách thông báo của tôi")
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyNotifications(@AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", notificationService.getMyNotifications(currentUser.getUserId())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Lấy số lượng thông báo chưa đọc")
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getUnreadCount(@AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", notificationService.getUnreadCount(currentUser.getUserId())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Đánh dấu 1 thông báo là đã đọc")
    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> markAsRead(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            notificationService.markAsRead(id, currentUser.getUserId());
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã đánh dấu đọc"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Đánh dấu tất cả là đã đọc")
    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> markAllAsRead(@AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            notificationService.markAllAsRead(currentUser.getUserId());
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã đánh dấu đọc tất cả"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
}
