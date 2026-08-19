package com.trilong.kpibackend.modules.post.entity;

import com.trilong.kpibackend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "social_posts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String platform; // Facebook, Zalo, TikTok, etc.

    /**
     * Loại nội dung lan tỏa — cùng nhóm "Lan tỏa giá trị" (5đ mỗi lượt), tách ra
     * để Admin theo dõi được nhân sự làm video hay chỉ đăng bài:
     * <ul>
     *   <li>{@code VIDEO} — video xây kênh (TikTok, Facebook cá nhân…)</li>
     *   <li>{@code POST}  — bài đăng / story chia sẻ trải nghiệm, công việc</li>
     * </ul>
     */
    @Column(name = "content_type", length = 20)
    @Builder.Default
    private String contentType = "POST";

    @Column(nullable = false, length = 500)
    private String link;

    @Column(columnDefinition = "TEXT")
    private String caption;

    @Column(name = "screenshot_url", length = 500)
    private String screenshotUrl;

    @Column(length = 50)
    @Builder.Default
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    private ZonedDateTime submittedAt;

    @Column(name = "approved_at")
    private ZonedDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @PrePersist
    public void prePersist() {
        if (this.status == null) this.status = "PENDING";
    }
}
