package com.trilong.kpibackend.modules.training.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * TrainingRsvp — Nhân sự trả lời có tham gia buổi đào tạo dự án hay không.
 *
 * <p>Đào tạo dự án bắt buộc mọi người dự. Nhưng ai không bán dự án đó thì không
 * có lý do phải ngồi học. Hệ thống không thể tự biết ai bán dự án nào, nên thay
 * vì bắt Admin dựng sẵn danh sách, để chính nhân sự tự khai rồi Admin duyệt:
 *
 * <ol>
 *   <li>Có buổi đào tạo dự án → mọi người nhận thông báo</li>
 *   <li>Chọn <b>Tham gia</b> → đến buổi học quét mã điểm danh như bình thường</li>
 *   <li>Chọn <b>Không tham gia</b> → bắt buộc nhập lý do</li>
 *   <li>Admin duyệt lý do → tính là có điểm danh, không bị mất điểm đào tạo</li>
 *   <li>Admin từ chối lý do → coi như vắng buổi đó</li>
 * </ol>
 *
 * <p>Cách này đổi một bài toán dữ liệu (ai bán dự án nào) thành một bước xác
 * nhận của con người, vốn là thứ Admin vẫn đang làm hằng ngày.
 */
@Entity
@Table(name = "training_rsvps",
        uniqueConstraints = @UniqueConstraint(name = "uk_rsvp_session_user",
                columnNames = {"session_id", "user_id"}),
        indexes = {
                @Index(name = "idx_rsvp_session", columnList = "session_id"),
                @Index(name = "idx_rsvp_status", columnList = "status")
        })
@Data
@NoArgsConstructor
public class TrainingRsvp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** {@code JOIN} = sẽ tham gia · {@code DECLINE} = xin không tham gia. */
    @Column(nullable = false, length = 10)
    private String choice;

    /** Lý do xin vắng — bắt buộc khi chọn không tham gia. */
    @Column(length = 500)
    private String reason;

    /**
     * Chỉ có ý nghĩa với lựa chọn không tham gia:
     * {@code PENDING} chờ Admin duyệt · {@code APPROVED} được chấp nhận ·
     * {@code REJECTED} bị từ chối.
     *
     * <p>Chọn tham gia thì để {@code PENDING} và không cần duyệt — điểm danh
     * thật lúc quét mã mới là căn cứ.
     */
    @Column(nullable = false, length = 10)
    private String status = "PENDING";

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private ZonedDateTime reviewedAt;

    /** Ghi chú của Admin khi duyệt hoặc từ chối. */
    @Column(name = "review_note", length = 300)
    private String reviewNote;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = ZonedDateTime.now();
        if (this.status == null) this.status = "PENDING";
    }
}
