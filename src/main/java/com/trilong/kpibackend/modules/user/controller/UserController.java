package com.trilong.kpibackend.modules.user.controller;

import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.core.service.CloudinaryService;
import com.trilong.kpibackend.modules.user.dto.CreateUserDTO;
import com.trilong.kpibackend.modules.user.dto.UpdateUserDTO;
import com.trilong.kpibackend.modules.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User / Nhân viên", description = "Quản lý tài khoản nhân viên (Admin/HR)")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserService userService;
    private final CloudinaryService cloudinaryService;
    private final com.trilong.kpibackend.modules.auth.service.PhienDangNhapService phienDangNhapService;
    private final com.trilong.kpibackend.modules.user.service.ReferralRewardService referralRewardService;

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

    @Operation(summary = "Các thiết bị đang đăng nhập tài khoản của một nhân sự",
               description = "Dành cho Admin: xem tài khoản đang được dùng ở những máy nào, " +
                             "kèm địa chỉ IP và lần gọi máy chủ gần nhất.")
    @GetMapping("/{id}/sessions")
    @PreAuthorize("hasAuthority('user:manage')")
    public ResponseEntity<?> danhSachPhien(@PathVariable Long id,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        // Chỉ đánh dấu "máy này" khi Admin đang xem chính tài khoản mình
        Long phienHienTai = id.equals(currentUser.getUserId()) ? currentUser.getSessionId() : null;
        return ResponseEntity.ok(Map.of("status", "SUCCESS",
                "data", phienDangNhapService.danhSach(id, phienHienTai)));
    }

    @Operation(summary = "Gỡ một thiết bị khỏi tài khoản của nhân sự",
               description = "Thu hồi đúng một phiên; các thiết bị khác của người đó vẫn đăng nhập.")
    @DeleteMapping("/{id}/sessions/{phienId}")
    @PreAuthorize("hasAuthority('user:manage')")
    public ResponseEntity<?> thuHoiPhien(@PathVariable Long id, @PathVariable Long phienId) {
        boolean xong = phienDangNhapService.thuHoi(id, phienId);
        return xong
                ? ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã gỡ thiết bị khỏi tài khoản."))
                : ResponseEntity.badRequest().body(Map.of("status", "ERROR",
                        "message", "Không tìm thấy phiên đăng nhập này."));
    }

    @Operation(summary = "Đăng xuất một nhân sự khỏi mọi thiết bị",
               description = "Thu hồi toàn bộ phiên và vô hiệu hóa mọi access token đã phát — " +
                             "kể cả token còn hạn đang nằm trên máy người khác.")
    @PostMapping("/{id}/logout-all")
    @PreAuthorize("hasAuthority('user:manage')")
    public ResponseEntity<?> dangXuatMoiThietBi(@PathVariable Long id) {
        try {
            int soPhien = phienDangNhapService.dangXuatMoiThietBi(id);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "soPhien", soPhien,
                    "message", "Đã đăng xuất khỏi " + soPhien + " thiết bị."));
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

    @Operation(summary = "Upload / Cập nhật ảnh đại diện của chính mình",
               description = "Nhân viên upload ảnh avatar mới. Ảnh được đẩy lên Cloudinary, URL được lưu vào DB.")
    @PostMapping(value = "/my-profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadMyAvatar(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam("file") MultipartFile file) {
        try {
            String avatarUrl = cloudinaryService.uploadImage(file);
            UpdateUserDTO dto = new UpdateUserDTO();
            dto.setAvatarUrl(avatarUrl);
            userService.updateUser(currentUser.getUserId(), dto);
            return ResponseEntity.ok(Map.of("status", "SUCCESS",
                    "data", Map.of("avatarUrl", avatarUrl)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("status", "ERROR", "message", "Upload avatar thất bại: " + e.getMessage()));
        }
    }

    @Operation(summary = "Chạy soát điểm gieo hạt nhân sự mới",
            description = "Cộng 15đ cho người giới thiệu khi nhân sự mới đã làm đủ một tháng và vẫn còn làm. "
                    + "Hệ thống tự chạy 23:20 mỗi ngày; API này để Admin chạy soát ngay.")
    @PostMapping("/referrals/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> runReferralRewards() {
        var result = referralRewardService.grantMaturedReferrals();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", result.granted() == 0
                        ? "Chưa có ai đủ điều kiện cộng điểm giới thiệu."
                        : "Đã cộng 15đ cho " + result.granted() + " người giới thiệu.",
                "data", Map.of("granted", result.granted(), "details", result.details())));
    }

    @Operation(summary = "XÓA VĨNH VIỄN nhân sự đã khóa cùng toàn bộ dữ liệu (chấm công, KPI, giao dịch, bài đăng, lương...). Không khôi phục được. Dành cho tài khoản thử nghiệm.")
    @DeleteMapping("/{id}/purge")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> purgeUser(@PathVariable Long id) {
        try {
            Map<String, Integer> daXoa = userService.purgeUser(id);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã xóa vĩnh viễn", "data", daXoa));
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
