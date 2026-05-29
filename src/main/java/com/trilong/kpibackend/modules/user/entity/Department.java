package com.trilong.kpibackend.modules.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "departments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Tọa độ GPS văn phòng của phòng ban/chi nhánh này
    @Column(name = "office_lat")
    private Double officeLat;

    @Column(name = "office_lng")
    private Double officeLng;

    // Bán kính cho phép check-in (mét), mặc định 50m
    @Column(name = "allowed_radius")
    @Builder.Default
    private Integer allowedRadius = 50;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
