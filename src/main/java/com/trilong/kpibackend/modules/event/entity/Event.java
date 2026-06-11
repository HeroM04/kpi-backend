package com.trilong.kpibackend.modules.event.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Sự kiện (trang Sự kiện của cổng SaleWeb). Có thể gắn với 1 dự án (projectId) hoặc sự kiện chung.
 */
@Entity
@Table(name = "events", schema = "salepro")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(unique = true, length = 255)
    private String slug;

    @Column(name = "event_type", length = 50)
    private String eventType; // GENERAL (Sự kiện chung), TRAINING (Đào tạo)

    @Column(length = 30)
    private String status; // UPCOMING (Sắp diễn ra), ENDED (Đã kết thúc)

    @Column(name = "banner_image", length = 500)
    private String bannerImage;

    @Column(columnDefinition = "TEXT")
    private String description; // Tổng quan (HTML)

    @Column(length = 500)
    private String location;

    @Column(name = "start_time")
    private ZonedDateTime startTime;

    @Column(name = "end_time")
    private ZonedDateTime endTime;

    @Column(name = "project_id")
    private Long projectId; // nullable — gắn sự kiện với dự án nếu có

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gallery_images", columnDefinition = "jsonb")
    private List<String> galleryImages; // tab "Thư viện"

    @Column(name = "participant_count")
    private Integer participantCount;

    @Column(name = "checkin_count")
    private Integer checkinCount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
