package com.trilong.kpibackend.modules.attendance.controller;

import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.modules.attendance.dto.CheckinRequestDTO;
import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.service.CheckinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.trilong.kpibackend.modules.user.repository.UserRepository;

/**
 * CheckinController — quản lý chấm công GPS.
 *
 * Bảo mật Phase 2:
 * - userId được lấy từ JWT token, KHÔNG từ request body (tránh giả mạo)
 * - Mọi endpoint đều yêu cầu Bearer token hợp lệ
 */
@RestController
@RequestMapping("/api/v1/attendance")
@Tag(name = "Attendance / Chấm công", description = "Check-in GPS tự động và ngoại tuyến")
@SecurityRequirement(name = "Bearer Authentication")
public class CheckinController {

    @Autowired
    private CheckinService checkinService;
    
    @Autowired
    private UserRepository userRepository;


    // ── POST /checkin ────────────────────────────────────────────────────────

    @Operation(
            summary = "Check-in chấm công",
            description = """
                    Nhân viên gửi ảnh selfie + tọa độ GPS để chấm công.
                    
                    **Luồng tự động:**
                    - GPS trong phạm vi văn phòng → `APPROVED` ngay lập tức
                    - GPS ngoài phạm vi → `OUT_OF_RANGE` → App hiện form nhập lý do
                    
                    **Bảo mật:** userId được lấy từ JWT token, không nhận từ client.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Check-in thành công hoặc OUT_OF_RANGE"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Không có quyền")
    })
    @PostMapping("/checkin")
    @PreAuthorize("hasAuthority('attendance:checkin')")
    public ResponseEntity<?> submitCheckin(
            @AuthenticationPrincipal UserPrincipal currentUser,   // userId từ JWT — an toàn
            @Valid @RequestBody CheckinRequestDTO request) {
        try {
            boolean isInside = checkinService.isWithinRange(
                    request.getLatitude(), request.getLongitude());

            if (!isInside) {
                return ResponseEntity.ok(Map.of(
                        "status", "OUT_OF_RANGE",
                        "message", "Bạn đang ở ngoài phạm vi văn phòng. Vui lòng nhập lý do."
                ));
            }

            // userId an toàn — lấy từ token, không tin client
            CheckinLog saved = checkinService.processCheckin(currentUser.getUserId(), request);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", saved));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    // ── POST /request-approval ───────────────────────────────────────────────

    @Operation(
            summary = "Gửi yêu cầu chấm công ngoại tuyến",
            description = """
                    Khi nhân viên ở ngoài phạm vi văn phòng (đi thị trường, gặp khách...),
                    gửi báo cáo kèm ảnh + lý do để chờ Admin/Trưởng phòng duyệt.
                    
                    Status sẽ là `PENDING` cho đến khi được duyệt.
                    """
    )
    @PostMapping("/request-approval")
    @PreAuthorize("hasAuthority('attendance:checkin')")
    public ResponseEntity<?> requestApproval(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CheckinRequestDTO request) {
        try {
            CheckinLog saved = checkinService.processFieldCheckin(currentUser.getUserId(), request);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Đã gửi yêu cầu. Chờ Admin/Trưởng phòng duyệt.",
                    "data", saved
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    // ── GET /my-checkins ─────────────────────────────────────────────────────

    @Operation(
            summary = "Lịch sử chấm công của tôi",
            description = "Lấy danh sách check-in của user đang đăng nhập. Có thể lọc theo tháng (YYYY-MM)."
    )
    @GetMapping("/my-checkins")
    @PreAuthorize("hasAuthority('attendance:view-my')")
    public ResponseEntity<?> getMyCheckins(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String date) {
        List<?> logs;
        if (date != null && !date.trim().isEmpty()) {
            // Lọc theo ngày cụ thể - chỉ query những bản ghi ngày đó
            LocalDate localDate = LocalDate.parse(date);
            logs = checkinService.getCheckinsByUserIdAndDate(currentUser.getUserId(), localDate);
        } else {
            // Mặc định: chỉ ngày hôm nay
            logs = checkinService.getCheckinsByUserIdAndDate(currentUser.getUserId(), LocalDate.now());
        }
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", logs));
    }

    // ── GET /pending ─────────────────────────────────────────────────────────

    @Operation(
            summary = "Danh sách chấm công chờ duyệt",
            description = "Chỉ Admin và Trưởng phòng mới truy cập được."
    )
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('attendance:approve')")
    public ResponseEntity<?> getPendingCheckins() {
        var logs = checkinService.getPendingCheckins();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", logs));
    }

    // ── POST /approve ────────────────────────────────────────────────────────

    @Operation(
            summary = "Duyệt đơn chấm công dã ngoại (Cũ)",
            description = "Duyệt hoặc từ chối yêu cầu chấm công ngoài văn phòng. Dành cho Admin/Trưởng phòng."
    )
    @PostMapping("/approve")
    @PreAuthorize("hasAuthority('attendance:approve') or hasRole('ADMIN')")
    public ResponseEntity<?> approveCheckin(@RequestBody Map<String, Object> request) {
        try {
            checkinService.processApproval(request);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Xử lý duyệt chấm công thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    // ── PUT /{id}/status ─────────────────────────────────────────────────────

    @Operation(
            summary = "Cập nhật trạng thái điểm danh (RESTful)",
            description = "Admin/Trưởng phòng có thể chuyển đổi trạng thái APPROVED / REJECTED của bản ghi chấm công."
    )
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('attendance:approve') or hasRole('ADMIN')")
    public ResponseEntity<?> updateAttendanceStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String reason) {
        try {
            CheckinLog updated = checkinService.updateStatus(id, status, reason);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS", 
                    "message", "Cập nhật trạng thái thành công",
                    "data", updated
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    // ── GET / (Admin list all) ───────────────────────────────────────────────

    @Operation(
            summary = "Lấy toàn bộ lịch sử chấm công",
            description = "Dành cho Admin và Văn phòng để xem và lọc lịch sử chấm công."
    )
    @GetMapping
    @PreAuthorize("hasAuthority('attendance:view-all') or hasRole('ADMIN')")
    public ResponseEntity<?> getAllCheckins(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String status) {
        try {
            var logs = checkinService.getAllCheckins();
            
            if (userId != null) {
                logs = logs.stream().filter(l -> l.getUserId().equals(userId)).toList();
            }
            if (departmentId != null) {
                // Lọc checkin log dựa trên danh sách user thuộc department đó
                logs = logs.stream().filter(l -> {
                    com.trilong.kpibackend.modules.user.entity.User u = userRepository.findById(l.getUserId()).orElse(null);
                    return u != null && u.getDepartment() != null && u.getDepartment().getId().equals(departmentId);
                }).toList();
            }
            if (month != null && !month.trim().isEmpty()) {
                logs = logs.stream()
                    .filter(l -> {
                        if (l.getCheckinTime() == null) return false;
                        String logMonth = String.format("%04d-%02d", 
                            l.getCheckinTime().getYear(), 
                            l.getCheckinTime().getMonthValue());
                        String logMonthAlt = String.format("%04d-%d", 
                            l.getCheckinTime().getYear(), 
                            l.getCheckinTime().getMonthValue());
                        return month.equals(logMonth) || month.equals(logMonthAlt) || logMonth.startsWith(month);
                    })
                    .toList();
            }
            if (status != null && !status.trim().isEmpty()) {
                logs = logs.stream().filter(l -> status.equalsIgnoreCase(l.getStatus())).toList();
            }
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", logs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    // ── GET /{id} (View detail) ──────────────────────────────────────────────

    @Operation(summary = "Xem chi tiết bản ghi chấm công")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getCheckinById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            var log = checkinService.getCheckinById(id);
            boolean isAdminOrStaff = currentUser.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("attendance:view-all") || a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("attendance:approve"));
            if (!isAdminOrStaff && !log.getUserId().equals(currentUser.getUserId())) {
                return ResponseEntity.status(403).body(Map.of("status", "ERROR", "message", "Bạn không có quyền xem bản ghi này."));
            }
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", log));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    // ── DELETE /{id} (Admin delete) ──────────────────────────────────────────

    @Operation(summary = "Xóa bản ghi chấm công", description = "Chỉ dành cho Admin.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteCheckin(@PathVariable Long id) {
        try {
            checkinService.deleteCheckin(id);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã xóa bản ghi chấm công thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
}