package com.trilong.kpibackend.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response sau khi login thành công.
 * App cần lưu cả 2 token vào secure storage.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LoginResponseDTO {

    // ── Tokens ─────────────────────────────────────────────────────────────
    private String accessToken;     // JWT ngắn hạn (1h) — gửi trong header mỗi request
    private String refreshToken;    // Token dài hạn (7 ngày) — dùng để lấy access mới
    private String tokenType;       // Luôn là "Bearer"
    private long   expiresIn;       // Số giây còn lại của access token

    // ── Thông tin user ─────────────────────────────────────────────────────
    private Long   userId;
    private String fullName;
    private String phoneNumber;
    private String role;            // SALE | ADMIN | TRUONG_PHONG | VAN_PHONG
    private String status;
    private java.util.List<String> permissions;
    private String avatarUrl;

    // ── Phòng ban + GPS ────────────────────────────────────────────────────
    // App dùng thông tin này để tính geofence check-in ngay mà không gọi API thêm
    private Long    departmentId;
    private String  departmentName;
    private Double  officeLat;
    private Double  officeLng;
    private Integer allowedRadius;  // Bán kính check-in văn phòng (mét)
}
