package com.trilong.kpibackend.modules.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * LeaveRequest — Đơn xin vắng của nhân sự.
 *
 * <p>Luồng nghiệp vụ:
 * <ul>
 *   <li>Nhân sự mở màn hình Điểm danh trên app → "Xin vắng" → chọn ngày + nhập lý do → gửi</li>
 *   <li>Admin vào WebAdmin duyệt hoặc từ chối</li>
 *   <li>Được duyệt → ngày đó tính là <b>vắng có phép</b>, trừ 10đ KPI</li>
 *   <li>Không gửi đơn (hoặc đơn bị từ chối) mà cũng không chấm công →
 *       cuối ngày hệ thống tự chấm <b>vắng không phép</b>, trừ 15đ KPI</li>
 * </ul>
 */
@Entity
@Table(name = "leave_requests",
        uniqueConstraints = @UniqueConstraint(name = "uk_leave_user_date", columnNames = {"user_id", "leave_date"}))
@Data
@NoArgsConstructor
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** userId lấy từ JWT token — không cho client tự truyền */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Ngày xin vắng */
    @Column(name = "leave_date", nullable = false)
    private LocalDate leaveDate;

    /** Lý do nhân sự tự nhập */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String reason;

    /** PENDING | APPROVED | REJECTED */
    @Column(length = 20, nullable = false)
    private String status;

    @Column(name = "submitted_at")
    private ZonedDateTime submittedAt;

    /** Admin duyệt/từ chối */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private ZonedDateTime reviewedAt;

    /** Ghi chú của Admin khi duyệt/từ chối */
    @Column(name = "review_note", length = 500)
    private String reviewNote;

    /**
     * Đã trừ điểm KPI cho đơn này hay chưa — chặn trừ hai lần khi Admin
     * bấm duyệt lại, hoặc khi scheduler chạy trùng ngày.
     */
    @Column(name = "kpi_applied", nullable = false)
    private Boolean kpiApplied = false;

    @PrePersist
    public void prePersist() {
        if (this.submittedAt == null) this.submittedAt = ZonedDateTime.now();
        if (this.status == null) this.status = "PENDING";
        if (this.kpiApplied == null) this.kpiApplied = false;
    }
}
