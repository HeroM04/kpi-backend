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

    @Column(length = 50)
    @Builder.Default
    private String status = "APPROVED"; // Tự động duyệt theo yêu cầu

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    private ZonedDateTime submittedAt;
}
