package com.trilong.kpibackend.modules.feedback.entity;

import com.trilong.kpibackend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Entity
@Table(name = "feedbacks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_id")
    private Long senderId; // ID người gửi

    @Column(name = "target_type", nullable = false)
    private String targetType; // 'COMPANY', 'HR', 'MANAGER'

    @Column(name = "target_id")
    private Long targetId; // Nếu targetType = MANAGER, ghi ID của Trưởng phòng vào đây

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(length = 50)
    @Builder.Default
    private String status = "UNREAD"; // 'UNREAD', 'READ', 'RESOLVED'

    @Column(name = "is_anonymous")
    private boolean isAnonymous; // Gửi ẩn danh hay không

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // --- CÁC TRƯỜNG MỚI BỔ SUNG ---
    
    private String title;

    private String category; // Khiếu nại điểm KPI, Góp ý tính năng...

    @Column(columnDefinition = "int default 5")
    @Builder.Default
    private Integer rating = 5;

    @Column(name = "admin_reply", columnDefinition = "TEXT")
    private String adminReply;

    @Column(name = "resolved_at")
    private ZonedDateTime resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @PrePersist
    public void prePersist() {
        if (this.status == null) this.status = "UNREAD";
        if (this.rating == null) this.rating = 5;
    }
}