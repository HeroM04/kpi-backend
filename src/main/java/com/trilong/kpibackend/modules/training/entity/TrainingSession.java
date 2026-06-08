package com.trilong.kpibackend.modules.training.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

@Entity
@Table(name = "training_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String presenter;

    @Column(name = "room_code", unique = true, length = 100)
    private String roomCode;

    @Column(name = "start_time")
    private ZonedDateTime startTime;

    private String location;

    @Column(name = "max_slots")
    @Builder.Default
    private Integer maxSlots = 20;

    @Column(length = 50)
    @Builder.Default
    private String status = "UPCOMING"; // UPCOMING, COMPLETED, CANCELLED

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    /**
     * Link video YouTube của buổi đào tạo.
     * Admin cập nhật sau khi buổi học kết thúc (status = COMPLETED).
     * Mobile App dùng url_launcher để mở YouTube App hoặc Browser.
     */
    @Column(name = "video_url", columnDefinition = "TEXT")
    private String videoUrl;

    @PrePersist
    public void prePersist() {
        if (this.status == null) this.status = "UPCOMING";
        if (this.maxSlots == null) this.maxSlots = 20;
    }
}
