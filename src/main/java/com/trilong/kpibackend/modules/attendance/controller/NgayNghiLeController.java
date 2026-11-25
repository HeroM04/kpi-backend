package com.trilong.kpibackend.modules.attendance.controller;

import com.trilong.kpibackend.modules.attendance.service.NgayNghiLeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ngay-nghi-le")
@RequiredArgsConstructor
@Tag(name = "Ngày nghỉ lễ", description = "Ngày nghỉ của cả công ty — không chốt vắng không phép")
public class NgayNghiLeController {

    private final NgayNghiLeService ngayNghiLeService;

    @Operation(summary = "Danh sách ngày nghỉ lễ")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> danhSach() {
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", ngayNghiLeService.danhSach()));
    }

    @Operation(summary = "Thêm ngày nghỉ lễ (Admin)",
            description = "Ngày đã qua thì hoàn luôn điểm vắng không phép / nghỉ có phép đã trừ của ngày đó.")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> them(@RequestBody Map<String, String> body) {
        try {
            LocalDate ngay = LocalDate.parse(body.getOrDefault("ngay", ""));
            var kq = ngayNghiLeService.them(ngay, body.get("ten"));
            String loi = kq.soNguoiHoan() > 0
                    ? "Đã thêm ngày nghỉ lễ và hoàn điểm vắng cho " + kq.soNguoiHoan() + " nhân sự (tổng " + kq.tongDiemHoan() + "đ)."
                    : "Đã thêm ngày nghỉ lễ.";
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", loi, "data", Map.of(
                    "ngayLe", kq.ngayLe(), "soNguoiHoan", kq.soNguoiHoan(), "tongDiemHoan", kq.tongDiemHoan())));
        } catch (java.time.format.DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "Ngày không hợp lệ (cần dạng yyyy-MM-dd)."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Xóa ngày nghỉ lễ (Admin)",
            description = "Không trừ lại điểm của ngày đã qua; chỉ là ngày đó không còn được miễn chốt vắng.")
    @DeleteMapping("/{ngay}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> xoa(@PathVariable String ngay) {
        try {
            ngayNghiLeService.xoa(LocalDate.parse(ngay));
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã xóa ngày nghỉ lễ."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
}
