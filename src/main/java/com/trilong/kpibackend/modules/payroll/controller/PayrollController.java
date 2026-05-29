package com.trilong.kpibackend.modules.payroll.controller;

import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.modules.payroll.dto.CalculatePayrollRequestDTO;
import com.trilong.kpibackend.modules.payroll.dto.PayrollResponseDTO;
import com.trilong.kpibackend.modules.payroll.entity.Payroll;
import com.trilong.kpibackend.modules.payroll.service.PayrollService;
import io.swagger.v3.oas.annotations.Hidden;
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
import java.util.stream.Collectors;

@Hidden
@RestController
@RequestMapping("/api/v1/payroll")
@RequiredArgsConstructor
@Tag(name = "Payroll / Lương bổng", description = "Tính toán, phê duyệt và xem chi tiết phiếu lương tháng")
@SecurityRequirement(name = "Bearer Authentication")
public class PayrollController {

    private final PayrollService payrollService;

    @Operation(
            summary = "Tính toán bảng lương tháng",
            description = """
                    Tính toán lương cho một nhân viên hoặc tất cả nhân viên trong tháng YYYY-MM.
                    
                    Quy trình tính toán:
                    - Thưởng KPI = điểm KPI * 50,000 VND.
                    - Phạt đi muộn = số lần check-in muộn (sau 08:30 sáng) * 100,000 VND.
                    - Lương thực nhận = Lương cứng + Thưởng KPI - Phạt đi muộn.
                    """
    )
    @PostMapping("/calculate")
    @PreAuthorize("hasAuthority('payroll:manage')")
    public ResponseEntity<?> calculatePayroll(@Valid @RequestBody CalculatePayrollRequestDTO request) {
        try {
            if (request.getUserId() != null) {
                Payroll calculated = payrollService.calculatePayrollForUser(request.getUserId(), request.getMonth());
                return ResponseEntity.ok(Map.of(
                        "status", "SUCCESS",
                        "message", "Tính toán lương thành công",
                        "data", PayrollResponseDTO.from(calculated)
                ));
            } else {
                List<Payroll> payrolls = payrollService.calculatePayrollForAll(request.getMonth());
                List<PayrollResponseDTO> dtos = payrolls.stream()
                        .map(PayrollResponseDTO::from)
                        .collect(Collectors.toList());
                return ResponseEntity.ok(Map.of(
                        "status", "SUCCESS",
                        "message", "Tính toán lương cho toàn bộ công ty thành công",
                        "data", dtos
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Phê duyệt phiếu lương tháng")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('payroll:manage')")
    public ResponseEntity<?> approvePayroll(@PathVariable Long id) {
        try {
            Payroll approved = payrollService.approvePayroll(id);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Đã phê duyệt phiếu lương thành công",
                    "data", PayrollResponseDTO.from(approved)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Xem danh sách phiếu lương cá nhân của tôi")
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('payroll:view-my')")
    public ResponseEntity<?> getMyPayrolls(@AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            List<Payroll> payrolls = payrollService.getMyPayrolls(currentUser.getUserId());
            List<PayrollResponseDTO> dtos = payrolls.stream()
                    .map(PayrollResponseDTO::from)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dtos));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Xem toàn bộ bảng lương tháng (Admin/Kế toán)")
    @GetMapping("/month/{month}")
    @PreAuthorize("hasAuthority('payroll:manage')")
    public ResponseEntity<?> getPayrollsByMonth(@PathVariable String month) {
        try {
            List<Payroll> payrolls = payrollService.getPayrollsByMonth(month);
            List<PayrollResponseDTO> dtos = payrolls.stream()
                    .map(PayrollResponseDTO::from)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dtos));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
}
