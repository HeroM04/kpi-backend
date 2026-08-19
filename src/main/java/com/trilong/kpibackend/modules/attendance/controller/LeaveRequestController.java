package com.trilong.kpibackend.modules.attendance.controller;

import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.modules.attendance.dto.LeaveRequestDTO;
import com.trilong.kpibackend.modules.attendance.entity.LeaveRequest;
import com.trilong.kpibackend.modules.attendance.service.LeaveRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/leave-requests")
@RequiredArgsConstructor
@Tag(name = "Leave Requests", description = "Đơn xin vắng — nhân sự gửi trên app, Admin duyệt trên web")
@SecurityRequirement(name = "Bearer Authentication")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    @Operation(summary = "Nhân sự gửi đơn xin vắng")
    @PostMapping
    @PreAuthorize("hasAuthority('attendance:checkin') or hasRole('ADMIN')")
    public ResponseEntity<?> submit(@AuthenticationPrincipal UserPrincipal currentUser,
                                    @Valid @RequestBody LeaveRequestDTO dto) {
        try {
            LeaveRequest req = leaveRequestService.submit(currentUser.getUserId(), dto);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Đã gửi đơn xin vắng, chờ Admin duyệt.",
                    "data", leaveRequestService.toDTO(req)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Xem đơn xin vắng của bản thân")
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('attendance:view-my') or hasRole('ADMIN')")
    public ResponseEntity<?> myRequests(@AuthenticationPrincipal UserPrincipal currentUser) {
        List<?> data = leaveRequestService.getMyRequests(currentUser.getUserId())
                .stream().map(leaveRequestService::toDTO).toList();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @Operation(summary = "Nhân sự hủy đơn đang chờ duyệt")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('attendance:checkin') or hasRole('ADMIN')")
    public ResponseEntity<?> cancel(@AuthenticationPrincipal UserPrincipal currentUser,
                                    @PathVariable Long id) {
        try {
            leaveRequestService.cancel(currentUser.getUserId(), id);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã hủy đơn xin vắng."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Admin xem đơn chờ duyệt")
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('attendance:approve') or hasRole('ADMIN')")
    public ResponseEntity<?> pending() {
        List<?> data = leaveRequestService.getPending().stream().map(leaveRequestService::toDTO).toList();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @Operation(summary = "Admin xem toàn bộ đơn xin vắng", description = "Lọc theo khoảng ngày nếu truyền from/to (yyyy-MM-dd).")
    @GetMapping
    @PreAuthorize("hasAuthority('attendance:view-all') or hasRole('ADMIN')")
    public ResponseEntity<?> all(@RequestParam(required = false) String from,
                                 @RequestParam(required = false) String to) {
        LocalDate f = (from != null && !from.isBlank()) ? LocalDate.parse(from) : null;
        LocalDate t = (to != null && !to.isBlank()) ? LocalDate.parse(to) : null;
        List<?> data = leaveRequestService.getAll(f, t).stream().map(leaveRequestService::toDTO).toList();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    @Operation(summary = "Admin duyệt đơn xin vắng", description = "Ghi nhận vắng có phép, trừ 10đ KPI.")
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('attendance:approve') or hasRole('ADMIN')")
    public ResponseEntity<?> approve(@PathVariable Long id,
                                     @AuthenticationPrincipal UserPrincipal currentUser,
                                     @RequestBody(required = false) Map<String, String> body) {
        try {
            String note = body != null ? body.get("note") : null;
            LeaveRequest req = leaveRequestService.approve(id, currentUser.getUserId(), note);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Đã duyệt vắng có phép (−10đ KPI).",
                    "data", leaveRequestService.toDTO(req)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Admin từ chối đơn xin vắng")
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('attendance:approve') or hasRole('ADMIN')")
    public ResponseEntity<?> reject(@PathVariable Long id,
                                    @AuthenticationPrincipal UserPrincipal currentUser,
                                    @RequestBody(required = false) Map<String, String> body) {
        try {
            String note = body != null ? body.get("note") : null;
            LeaveRequest req = leaveRequestService.reject(id, currentUser.getUserId(), note);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Đã từ chối đơn xin vắng.",
                    "data", leaveRequestService.toDTO(req)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Admin chốt vắng mặt thủ công cho một ngày",
            description = "Chấm vắng không phép (−15đ) cho ai không chấm công và không có đơn được duyệt. "
                    + "Bình thường hệ thống tự chạy lúc 23:30 mỗi ngày, API này để chạy bù.")
    @PostMapping("/close-day")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> closeDay(@RequestParam(required = false) String date) {
        LocalDate d = (date != null && !date.isBlank())
                ? LocalDate.parse(date)
                : LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        int count = leaveRequestService.closeDay(d);
        Map<String, Object> res = new HashMap<>();
        res.put("status", "SUCCESS");
        res.put("message", "Đã chốt ngày " + d + ": " + count + " nhân sự vắng không phép.");
        res.put("data", Map.of("date", d.toString(), "unexcusedCount", count));
        return ResponseEntity.ok(res);
    }
}
