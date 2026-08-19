package com.trilong.kpibackend.modules.kpi.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * KpiAutoGrant — Dấu vết mỗi lần hệ thống tự cộng điểm KPI cho nhân sự.
 *
 * <p>Không phải điểm nào cũng đến từ một hành động có bản ghi riêng (chấm công,
 * bài đăng, thực chiến…). Có những khoản hệ thống tự cộng theo quy định, ví dụ
 * tuần công ty không tổ chức đào tạo thì mọi người mặc định được 15đ. Bảng này
 * lưu lại từng khoản như vậy để:
 * <ul>
 *   <li>Chạy lại scheduler không bị cộng trùng (ràng buộc duy nhất theo
 *       nhân sự + kỳ + loại)</li>
 *   <li>Giải thích được điểm ở đâu ra khi đối chiếu báo cáo</li>
 * </ul>
 */
@Entity
@Table(name = "kpi_auto_grants",
        uniqueConstraints = @UniqueConstraint(name = "uk_auto_grant",
                columnNames = {"user_id", "period", "grant_type"}))
@Data
@NoArgsConstructor
public class KpiAutoGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Kỳ áp dụng — tuần ISO ("2026-W34") hoặc tháng ("2026-08") tùy loại. */
    @Column(nullable = false, length = 20)
    private String period;

    /** Loại điểm tự cộng, ví dụ {@code NO_TRAINING_WEEK}. */
    @Column(name = "grant_type", nullable = false, length = 40)
    private String grantType;

    /** Nhóm điểm trong bảng KPI tuần (attendance / meeting / post…). */
    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false)
    private Integer points;

    /** Diễn giải hiển thị cho Admin khi đối chiếu. */
    @Column(length = 300)
    private String reason;

    @Column(name = "granted_at")
    private ZonedDateTime grantedAt;

    @PrePersist
    public void prePersist() {
        if (this.grantedAt == null) this.grantedAt = ZonedDateTime.now();
    }
}
