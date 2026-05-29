package com.trilong.kpibackend.modules.user.controller;

import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.modules.user.dto.CreateUserDTO;
import com.trilong.kpibackend.modules.user.dto.UpdateUserDTO;
import com.trilong.kpibackend.modules.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User / Nhân viên", description = "Quản lý tài khoản nhân viên (Admin/HR)")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Lấy danh sách nhân viên (có thể lọc theo phòng ban, role, trạng thái)")
    @GetMapping
    @PreAuthorize("hasAuthority('user:manage')")
    public ResponseEntity<?> getAllUsers(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        try {
            return ResponseEntity.ok(Map.of("status", "SUCCESS",
                    "data", userService.getUsersByFilters(departmentId, role, status)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Lấy chi tiết nhân viên theo ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", userService.getUserById(id)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Xem profile cá nhân — dành cho Mobile home screen",
               description = "Trả về thông tin user hiện tại kèm điểm KPI tháng này và số ngày đã chấm công.")
    @GetMapping("/my-profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            return ResponseEntity.ok(Map.of("status", "SUCCESS",
                    "data", userService.getMyProfile(currentUser.getUserId())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Tạo mới tài khoản nhân viên")
    @PostMapping
    @PreAuthorize("hasAuthority('user:manage')")
    public ResponseEntity<?> createUser(@Valid @RequestBody CreateUserDTO dto) {
        try {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", userService.createUser(dto)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Cập nhật thông tin tài khoản nhân viên")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody UpdateUserDTO dto) {
        try {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", userService.updateUser(id, dto)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Admin reset mật khẩu nhân viên",
               description = "Admin đặt lại mật khẩu mới cho nhân viên mà không cần biết mật khẩu cũ.")
    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('user:manage')")
    public ResponseEntity<?> resetPassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            String newPassword = body.get("newPassword");
            if (newPassword == null || newPassword.trim().length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("status", "ERROR",
                        "message", "Mật khẩu mới phải có ít nhất 6 ký tự."));
            }
            userService.resetPassword(id, newPassword);
            return ResponseEntity.ok(Map.of("status", "SUCCESS",
                    "message", "Đặt lại mật khẩu thành công."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Cập nhật trạng thái nhân viên (ACTIVE / INACTIVE / SUSPENDED)")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user:manage')")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            String newStatus = body.get("status");
            if (newStatus == null || newStatus.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("status", "ERROR",
                        "message", "Trạng thái không được để trống."));
            }
            userService.updateStatus(id, newStatus.toUpperCase());
            return ResponseEntity.ok(Map.of("status", "SUCCESS",
                    "message", "Cập nhật trạng thái thành công."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Vô hiệu hóa tài khoản nhân viên (Xóa mềm)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã vô hiệu hóa tài khoản thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
}
