package com.trilong.kpibackend.modules.user.controller;

import com.trilong.kpibackend.modules.user.dto.DepartmentDTO;
import com.trilong.kpibackend.modules.user.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@Tag(name = "Department / Phòng ban", description = "Quản lý phòng ban và tọa độ định vị GPS văn phòng")
@SecurityRequirement(name = "Bearer Authentication")
public class DepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "Lấy danh sách tất cả phòng ban")
    @GetMapping
    @PreAuthorize("hasAuthority('department:manage')")
    public ResponseEntity<?> getAllDepartments() {
        try {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", departmentService.getAllDepartments()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Lấy chi tiết phòng ban theo ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('department:manage')")
    public ResponseEntity<?> getDepartmentById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", departmentService.getDepartmentById(id)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Tạo phòng ban mới")
    @PostMapping
    @PreAuthorize("hasAuthority('department:manage')")
    public ResponseEntity<?> createDepartment(@Valid @RequestBody DepartmentDTO dto) {
        try {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", departmentService.createDepartment(dto)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Cập nhật thông tin phòng ban")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('department:manage')")
    public ResponseEntity<?> updateDepartment(@PathVariable Long id, @RequestBody DepartmentDTO dto) {
        try {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", departmentService.updateDepartment(id, dto)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Xóa phòng ban")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('department:manage')")
    public ResponseEntity<?> deleteDepartment(@PathVariable Long id) {
        try {
            departmentService.deleteDepartment(id);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã xóa phòng ban thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
}
