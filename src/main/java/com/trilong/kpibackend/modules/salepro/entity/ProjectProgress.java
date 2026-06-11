package com.trilong.kpibackend.modules.salepro.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Mốc tiến độ dự án — tab "Tiến độ". Mỗi mốc có ngày, link ngoài (Drive) và gallery ảnh tiến độ.
 */
@Entity
@Table(name = "project_progress", schema = "salepro")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(length = 150)
    private String title; // vd "Tháng 6/2026"

    @Column(name = "progress_date")
    private LocalDate progressDate;

    @Column(name = "external_url", length = 500)
    private String externalUrl; // link ngoài (Drive) khi bấm icon

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> images;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
