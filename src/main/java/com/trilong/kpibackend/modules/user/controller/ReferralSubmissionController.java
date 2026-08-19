package com.trilong.kpibackend.modules.user.controller;

import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.modules.user.dto.ApproveReferralDTO;
import com.trilong.kpibackend.modules.user.dto.ReferralSubmissionDTO;
import com.trilong.kpibackend.modules.user.dto.UserDTO;
import com.trilong.kpibackend.modules.user.entity.ReferralSubmission;
import com.trilong.kpibackend.modules.user.service.ReferralSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/referral-submissions")
@RequiredArgsConstructor
@Tag(name = "Referral Submissions", description = "Đơn giới thiệu nhân sự mới — Sale gửi trên app, Admin duyệt trên web")
@SecurityRequirement(name = "Bearer Authentication")
public class ReferralSubmissionController {

    private final ReferralSubmissionService service;

    @Operation(summary = "Nhân sự gửi đơn giới thiệu ứng viên")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> submit(@AuthenticationPrincipal UserPrincipal currentUser,
                                    @Valid @RequestBody ReferralSubmissionDTO dto) {
        try {
            ReferralSubmission saved = service.submit(currentUser.getUserId(), dto);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Đã gửi đơn giới thiệu, chờ Admin duyệt.",
                    "data", service.toDTO(saved)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Xem các đơn giới thiệu của bản thân")
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> mySubmissions(@AuthenticationPrincipal UserPrincipal currentUser) {
        List<?> data = service.getMySubmissions(currentUser.getUserId()).stream().map(service::toDTO).toList();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @Operation(summary = "Nhân sự rút đơn đang chờ duyệt")
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> cancel(@AuthenticationPrincipal UserPrincipal currentUser, @PathVariable Long id) {
        try {
            service.cancel(currentUser.getUserId(), id);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã rút đơn giới thiệu."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Admin xem đơn giới thiệu chờ duyệt")
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('user:manage') or hasRole('ADMIN')")
    public ResponseEntity<?> pending() {
        List<?> data = service.getPending().stream().map(service::toDTO).toList();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @Operation(summary = "Admin xem toàn bộ đơn giới thiệu")
    @GetMapping
    @PreAuthorize("hasAuthority('user:manage') or hasRole('ADMIN')")
    public ResponseEntity<?> all() {
        List<?> data = service.getAll().stream().map(service::toDTO).toList();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @Operation(summary = "Admin duyệt đơn và mở tài khoản cho nhân sự mới",
            description = "Tạo tài khoản với người giới thiệu gắn sẵn. Một tháng sau, nếu nhân sự mới "
                    + "vẫn còn làm thì người giới thiệu được +15đ nhóm Lan tỏa.")
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('user:manage') or hasRole('ADMIN')")
    public ResponseEntity<?> approve(@PathVariable Long id,
                                     @AuthenticationPrincipal UserPrincipal currentUser,
                                     @RequestBody ApproveReferralDTO dto) {
        try {
            UserDTO created = service.approve(id, currentUser.getUserId(), dto);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Đã duyệt và tạo tài khoản cho " + created.getFullName() + ".",
                    "data", created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Admin từ chối đơn giới thiệu")
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('user:manage') or hasRole('ADMIN')")
    public ResponseEntity<?> reject(@PathVariable Long id,
                                    @AuthenticationPrincipal UserPrincipal currentUser,
                                    @RequestBody(required = false) Map<String, String> body) {
        try {
            String note = body != null ? body.get("note") : null;
            ReferralSubmission r = service.reject(id, currentUser.getUserId(), note);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Đã từ chối đơn giới thiệu.",
                    "data", service.toDTO(r)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
}
