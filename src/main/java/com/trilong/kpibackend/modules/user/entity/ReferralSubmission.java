package com.trilong.kpibackend.modules.user.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * ReferralSubmission — Đơn giới thiệu ứng viên do nhân sự gửi trên app.
 *
 * <p>Luồng nghiệp vụ hai đầu:
 * <ol>
 *   <li>Sale mở app → "Giới thiệu người mới" → nhập tên, số điện thoại, ghi chú → gửi</li>
 *   <li>Admin vào WebAdmin xem đơn, chọn phòng ban + vai trò + ngày vào làm rồi duyệt</li>
 *   <li>Duyệt xong hệ thống tự tạo tài khoản nhân sự mới, gắn sẵn người giới thiệu</li>
 *   <li>Một tháng sau, nếu người mới vẫn còn làm thì người giới thiệu được +15đ
 *       (xem {@code ReferralRewardService})</li>
 * </ol>
 *
 * <p>Admin vẫn tạo tay nhân sự mới ở trang Nhân sự như trước và chọn người giới
 * thiệu trực tiếp — đơn này chỉ là đường thứ hai đi từ phía nhân sự.
 */
@Entity
@Table(name = "referral_submissions")
@Data
@NoArgsConstructor
public class ReferralSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nhân sự gửi đơn — chính là người giới thiệu, lấy từ JWT */
    @Column(name = "referrer_id", nullable = false)
    private Long referrerId;

    @Column(name = "candidate_name", nullable = false, length = 150)
    private String candidateName;

    @Column(name = "candidate_phone", nullable = false, length = 20)
    private String candidatePhone;

    /** Giới thiệu thêm về ứng viên: kinh nghiệm, mối quan hệ, ghi chú… */
    @Column(columnDefinition = "TEXT")
    private String note;

    /** PENDING | APPROVED | REJECTED */
    @Column(length = 20, nullable = false)
    private String status;

    @Column(name = "submitted_at")
    private ZonedDateTime submittedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private ZonedDateTime reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    /** Tài khoản nhân sự được tạo ra khi Admin duyệt đơn */
    @Column(name = "created_user_id")
    private Long createdUserId;

    /** Ngày vào làm Admin ấn định lúc duyệt — mốc tính tròn một tháng */
    @Column(name = "joined_date")
    private LocalDate joinedDate;

    @PrePersist
    public void prePersist() {
        if (this.submittedAt == null) this.submittedAt = ZonedDateTime.now();
        if (this.status == null) this.status = "PENDING";
    }
}
