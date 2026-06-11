package com.trilong.kpibackend.modules.salepro.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "buildings", schema = "salepro")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "building_name", nullable = false, length = 100)
    private String buildingName;

    @Column(name = "subdivision_name", length = 100)
    private String subdivisionName;

    @Column(name = "total_floors")
    private Integer totalFloors;

    // ===== Bổ sung cho sub-tab "Tổng quan tòa" (THÔNG TIN CHUNG) =====
    @Column(name = "ownership_type", length = 100)
    private String ownershipType; // Hình thức sở hữu, vd "Lâu dài"

    @Column(name = "handover_standard", length = 100)
    private String buildingHandoverStandard; // Tiêu chuẩn bàn giao tòa, vd "Cao cấp"

    @Column(name = "total_area", precision = 14, scale = 2)
    private BigDecimal totalArea; // Tổng diện tích

    @Column(name = "total_apartments")
    private Integer totalApartments; // Số căn hộ công bố (vd 1000) — khác số căn thực tế

    @Column(name = "elevator_count")
    private Integer elevatorCount; // Số thang máy

    @Column(columnDefinition = "TEXT")
    private String description; // mô tả/giới thiệu tòa

    @Column(name = "image_url", length = 500)
    private String imageUrl; // ảnh tòa

    @Column(name = "construction_progress")
    private Integer constructionProgress; // tiến độ xây dựng %, vd 33

    @Column(name = "sales_policy", columnDefinition = "TEXT")
    private String salesPolicy; // CSBH riêng của tòa

    // ===== Marker trên Masterplan (tab Mặt bằng) =====
    @Column(name = "marker_lat")
    private Double markerLat;

    @Column(name = "marker_lng")
    private Double markerLng;
}
