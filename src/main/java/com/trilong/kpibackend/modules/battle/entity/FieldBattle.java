package com.trilong.kpibackend.modules.battle.entity;

import com.trilong.kpibackend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "field_battles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldBattle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Column(nullable = false)
    private String project;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "location", length = 500)
    private String location; // Tên đường phố (reverse geocoding từ GPS Mobile)

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

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
