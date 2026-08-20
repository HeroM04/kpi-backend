package com.trilong.kpibackend.modules.attendance.controller;

import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.modules.attendance.dto.CheckinRequestDTO;
import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.service.CheckinService;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
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
import java.util.HashMap;
import java.util.Map;

/**
 * CheckinController — Quản lý API chấm công GPS.
 *
 * <h3>Endpoint chính:</h3>
 * <pre>
 *   POST /api/v1/attendance/checkin
 *     → GPS ≤ 50m: APPROVED + cộng KPI
 *     → GPS > 50m: PENDING (bắt buộc có note)
 * </pre>
 *
 * <h3>Quy trình Mobile App trước khi gọi API này:</h3>
 * <ol>
 *   <li>Kiểm tra mock location → block nếu fake GPS</li>
 *   <li>Mở Camera (tắt gallery) → chụp ảnh</li>
 *   <li>ML Kit face detection → phải có ≥ 1 khuôn mặt</li>
 *   <li>Reverse Geocoding → lấy địa chỉ</li>
 *   <li>Vẽ watermark (timestamp + địa chỉ) lên ảnh</li>
 *   <li>Upload ảnh lên Cloudinary → lấy photoUrl</li>
 *   <li>Gọi API này với đầy đủ: lat, lng, address, photoUrl, note (nếu ngoại tuyến)</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/attendance")
@Tag(name = "Attendance / Chấm công", description = "Check-in GPS tự động — Văn phòng (APPROVED) hoặc Ngoại tuyến (PENDING)")
@SecurityRequirement(name = "Bearer Authentication")
public class CheckinController {

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private UserRepository userRepository;

    // ═══════════════════════════════════════════════════════════════════════
    // MOBILE APP — Check-in
    // ═══════════════════════════════════════════════════════════════════════

    @Operation(
            summary = "📍 Chấm công GPS (Unified)",
            description = """
                    **Endpoint duy nhất** cho cả 2 luồng chấm công:
                    
                    **Luồng 1 — Tại văn phòng (GPS ≤ 50m):**
                    - Tự động `APPROVED` + cộng KPI ngay lập tức
                    - Mốc KPI: check-in trước 08:30 = đúng giờ
                    
                    **Luồng 2 — Ngoại tuyến/Thị trường (GPS > 50m):**
                    - Status: `PENDING` — chờ Admin/Trưởng phòng duyệt
                    - Trường `note` là **BẮT BUỘC** — nếu thiếu sẽ báo lỗi 400
                    
                    **Yêu cầu Mobile App phải thực hiện trước khi gọi API này:**
                    - ✅ Anti-mock location: block nếu isMockLocation = true
                    - ✅ Camera-only: không cho chọn từ gallery
                    - ✅ ML Kit face detection: ≥ 1 khuôn mặt trong ảnh
                    - ✅ Watermark ảnh: timestamp + địa chỉ ở góc dưới trái
                    - ✅ Upload ảnh lên Cloudinary → truyền photoUrl
                    
                    **userId được lấy từ JWT token — không nhận từ client.**
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chấm công thành công (APPROVED hoặc PENDING)"),
            @ApiResponse(responseCode = "400", description = "Thiếu note khi ngoại tuyến, hoặc thiếu trường bắt buộc"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập / token hết hạn"),
            @ApiResponse(responseCode = "403", description = "Không có quyền chấm công")
    })
    @PostMapping("/checkin")
    @PreAuthorize("hasAuthority('attendance:checkin')")
    public ResponseEntity<?> submitCheckin(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CheckinRequestDTO request) {
        try {
            CheckinLog saved = checkinService.submitCheckin(currentUser.getUserId(), request);

            String message = "OFFICE".equals(saved.getCheckinType())
                    ? "✅ Chấm công tại văn phòng thành công! KPI đã được cộng."
                    : "⏳ Đã gửi yêu cầu chấm công ngoại tuyến. Chờ Admin/Trưởng phòng duyệt.";

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "checkinType", saved.getCheckinType(),
                    "approvalStatus", saved.getStatus(),
                    "message", message,
                    "data", saved
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("status", "ERROR", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("status", "ERROR", "message", "Lỗi hệ thống: " + e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MOBILE APP — Lịch sử cá nhân
    // ═══════════════════════════════════════════════════════════════════════

    @Operation(
            summary = "📋 Lịch sử chấm công của tôi",
            description = "Lấy danh sách check-in của user đang đăng nhập. Mặc định là hôm nay, có thể lọc theo ngày (YYYY-MM-DD)."
    )
    @GetMapping("/my-checkins")
    @PreAuthorize("hasAuthority('attendance:view-my')")
    public ResponseEntity<?> getMyCheckins(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String date) {
        try {
            LocalDate targetDate = (date != null && !date.trim().isEmpty())
                    ? LocalDate.parse(date)
                    : LocalDate.now();
            List<CheckinLog> logs = checkinService.getCheckinsByUserIdAndDate(currentUser.getUserId(), targetDate);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", logs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ADMIN / TRƯỞNG PHÒNG — Quản lý duyệt
    // ═══════════════════════════════════════════════════════════════════════

    @Operation(
            summary = "⏳ Danh sách chấm công chờ duyệt",
            description = "Lấy tất cả bản ghi PENDING. Chỉ Admin và Trưởng phòng truy cập được."
    )
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('attendance:approve')")
    public ResponseEntity<?> getPendingCheckins() {
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", checkinService.getPendingCheckins()
        ));
    }

    @Operation(
            summary = "✅❌ Duyệt / Từ chối chấm công ngoại tuyến",
            description = """
                    Admin hoặc Trưởng phòng duyệt / từ chối yêu cầu chấm công PENDING.
                    
                    - `status`: APPROVED hoặc REJECTED
                    - `reason`: Lý do từ chối (bắt buộc khi REJECTED)
                    
                    Khi APPROVED: hệ thống tự động cộng KPI attendance.
                    Khi REJECTED: nếu trước đó đã APPROVED sẽ trừ KPI lại.
                    """
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

    @Operation(
            summary = "🗓️ Lấy lịch sử chấm công theo trang (Admin)",
            description = """
                    Trả về MỘT TRANG bản ghi, không phải toàn bộ bảng.

                    Lọc: userId, departmentId, search (tên nhân sự), status, from/to (yyyy-MM-dd),
                    hoặc month (yyyy-MM) cho tương thích với bản cũ.
                    Phân trang: page (từ 0), size (mặc định 20, tối đa 200).

                    Trước đây endpoint này nạp cả bảng vào bộ nhớ rồi mới lọc bằng Java. Với
                    200 nhân sự chấm công hằng ngày, bảng lên hơn 100.000 dòng một năm và cách
                    làm đó khiến máy chủ hết bộ nhớ. Nay cơ sở dữ liệu tự lọc, tự sắp xếp và
                    chỉ trả về đúng số dòng của trang đang xem.
                    """
    )
    @GetMapping
    @PreAuthorize("hasAuthority('attendance:view-all') or hasRole('ADMIN')")
    public ResponseEntity<?> getAllCheckins(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            var ketQua = checkinService.timTheoTrang(
                    userId, departmentId, search, month, from, to, status, page, size);

            Map<String, Object> res = new HashMap<>();
            res.put("status", "SUCCESS");
            res.put("data", ketQua.getContent());
            res.put("page", Map.of(
                    "number", ketQua.getNumber(),
                    "size", ketQua.getSize(),
                    "totalElements", ketQua.getTotalElements(),
                    "totalPages", ketQua.getTotalPages()));
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "🔍 Xem chi tiết bản ghi chấm công")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getCheckinById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            var log = checkinService.getCheckinById(id);
            boolean isAdminOrStaff = currentUser.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("attendance:view-all")
                            || a.getAuthority().equals("ROLE_ADMIN")
                            || a.getAuthority().equals("attendance:approve"));
            if (!isAdminOrStaff && !log.getUserId().equals(currentUser.getUserId())) {
                return ResponseEntity.status(403)
                        .body(Map.of("status", "ERROR", "message", "Bạn không có quyền xem bản ghi này."));
            }
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", log));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "🗑️ Xóa bản ghi chấm công", description = "Chỉ dành cho Admin.")
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

    // ── Backward compatible (POST /approve) ─────────────────────────────────
    @Operation(summary = "Duyệt checkin (API cũ — dùng PUT /{id}/status thay thế)", deprecated = true)
    @PostMapping("/approve")
    @PreAuthorize("hasAuthority('attendance:approve') or hasRole('ADMIN')")
    public ResponseEntity<?> approveCheckinLegacy(@RequestBody Map<String, Object> request) {
        try {
            checkinService.processApproval(request);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Xử lý duyệt chấm công thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
}