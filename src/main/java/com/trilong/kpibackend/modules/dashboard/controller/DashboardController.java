package com.trilong.kpibackend.modules.dashboard.controller;

import com.trilong.kpibackend.modules.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Xem thống kê tổng quan và số lượng chờ duyệt")
@SecurityRequirement(name = "Bearer Authentication")
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Lấy thống kê tổng quan doanh nghiệp (Web Admin)",
               description = "Có thể lọc số liệu chốt deal (doanh thu, hoa hồng) theo tháng.")
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('feedback:manage') or hasAuthority('payroll:manage')")
    public ResponseEntity<?> getOverallStats(@RequestParam(required = false) String month) {
        try {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dashboardService.getOverallStats(month)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Lấy top nhân viên xuất sắc trong tháng",
               description = "Lấy danh sách 5 nhân viên có điểm KPI cao nhất trong tháng.")
    @GetMapping("/top-performers")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('kpi:view-all')")
    public ResponseEntity<?> getTopPerformers(@RequestParam(required = false) String month) {
        try {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dashboardService.getTopPerformers(month)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Lấy số lượng chờ duyệt làm badge menu (Web/Mobile)")
    @GetMapping("/menu-badges")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMenuBadges() {
        try {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dashboardService.getMenuBadges()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
}

