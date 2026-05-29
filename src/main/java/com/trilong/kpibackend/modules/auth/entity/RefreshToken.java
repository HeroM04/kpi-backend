package com.trilong.kpibackend.modules.auth.entity;

import com.trilong.kpibackend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

/**
 * RefreshToken — lưu refresh token trong DB để có thể revoke.
 *
 * Tại sao lưu DB thay vì dùng JWT hoàn toàn?
 * - Cho phép logout thực sự (revoke token)
 * - Hỗ trợ multi-device (mỗi thiết bị 1 refresh token)
 * - Phát hiện token reuse (bảo mật nâng cao)
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_token_token", columnList = "token"),
        @Index(name = "idx_refresh_token_user_id", columnList = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // UUID token lưu dưới dạng plain text (không nhạy cảm như password)
    @Column(nullable = false, unique = true, length = 500)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;

    @Column(name = "is_revoked")
    @Builder.Default
    private boolean revoked = false;

    // Thông tin thiết bị — hỗ trợ quản lý phiên đăng nhập
    @Column(name = "device_info", length = 200)
    private String deviceInfo; // e.g. "Flutter/Android", "Web/Chrome"

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    /** Kiểm tra token còn hạn sử dụng không */
    public boolean isExpired() {
        return ZonedDateTime.now().isAfter(expiresAt);
    }

    /** Token hợp lệ khi chưa bị revoke VÀ chưa hết hạn */
    public boolean isValid() {
        return !revoked && !isExpired();
    }
}
