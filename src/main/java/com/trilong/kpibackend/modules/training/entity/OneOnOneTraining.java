package com.trilong.kpibackend.modules.training.entity;

import com.trilong.kpibackend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "training_one_on_one")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OneOnOneTraining {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    /**
     * PENDING → Admin duyệt (APPROVED, cộng điểm) hoặc từ chối (REJECTED).
     *
     * <p>Trước đây nộp là tự duyệt và cộng điểm ngay, nên chụp đại một tấm ảnh
     * cũng có 5đ, Admin chỉ nhìn thấy sau khi điểm đã vào. Các báo cáo nộp trước
     * khi đổi vẫn giữ APPROVED — Admin xem lại được và từ chối để thu hồi điểm.
     */
    @Column(length = 50)
    @Builder.Default
    private String status = "PENDING";

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    private ZonedDateTime submittedAt;

    /**
     * Id người duyệt hoặc từ chối. Null với báo cáo tự duyệt đời cũ. Lưu id trần
     * chứ không nối khóa ngoại: xóa vĩnh viễn một tài khoản Admin thì không bị
     * DB chặn vì còn báo cáo nó từng duyệt.
     */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private ZonedDateTime reviewedAt;
}
