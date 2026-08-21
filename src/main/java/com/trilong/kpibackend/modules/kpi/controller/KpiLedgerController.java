package com.trilong.kpibackend.modules.kpi.controller;

import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.modules.kpi.service.KpiLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Nhật ký điểm KPI — nguồn dữ liệu cho màn hình Thông báo trên ứng dụng.
 *
 * <p>Mỗi lần cộng hay trừ điểm là một dòng kèm câu giải thích ngắn. Xem được
 * theo tuần hoặc theo tháng, lùi về các kỳ trước bằng tham số {@code offset}.
 */
@RestController
@RequestMapping("/api/v1/kpi-ledger")
@RequiredArgsConstructor
@Tag(name = "KPI Ledger", description = "Lịch sử từng khoản điểm KPI được cộng và bị trừ")
public class KpiLedgerController {

    private final KpiLedgerService kpiLedgerService;

    @Operation(
            summary = "Nhật ký điểm KPI của chính mình",
            description = "type=week (mặc định) hoặc month. offset=0 là kỳ hiện tại, 1 là kỳ liền trước.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> nhatKyCuaToi(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(defaultValue = "week") String type,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(Map.of("status", "SUCCESS",
                "data", kpiLedgerService.nhatKy(currentUser.getUserId(), type, offset)));
    }

    @Operation(
            summary = "Số khoản điểm mới chưa xem",
            description = "Dùng cho huy hiệu đỏ trên biểu tượng Thông báo.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/my/unread")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> soChuaDoc(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(Map.of("status", "SUCCESS",
                "data", Map.of("unread", kpiLedgerService.soChuaDoc(currentUser.getUserId()))));
    }

    @Operation(
            summary = "Đánh dấu đã xem hết thông báo điểm",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping("/my/seen")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> danhDauDaXem(@AuthenticationPrincipal UserPrincipal currentUser) {
        kpiLedgerService.danhDauDaXem(currentUser.getUserId());
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã đánh dấu xem hết."));
    }

    @Operation(
            summary = "Nhật ký điểm KPI của một nhân sự (Admin / Trưởng phòng)",
            description = "Để giải đáp khi nhân sự thắc mắc điểm của mình ở đâu ra.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('kpi:view-all') or hasRole('ADMIN') or hasRole('TRUONG_PHONG')")
    public ResponseEntity<?> nhatKyNhanSu(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "week") String type,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(Map.of("status", "SUCCESS",
                "data", kpiLedgerService.nhatKy(userId, type, offset)));
    }
}
