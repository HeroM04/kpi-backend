package com.trilong.kpibackend.modules.salepro.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(name = "apartments", schema = "salepro")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Apartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(name = "apartment_code", nullable = false, unique = true, length = 50)
    private String apartmentCode;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "apartment_type", length = 50)
    private String apartmentType;

    @Column(length = 50)
    private String direction;

    @Column(length = 10)
    private String floor;

    @Column(length = 10)
    private String axis;

    @Column(name = "view_description", columnDefinition = "TEXT")
    private String viewDescription;

    @Column(length = 50)
    private String status;

    // Nhóm Diện tích (đơn vị: m2)
    @Column(name = "clearance_area", precision = 10, scale = 2)
    private BigDecimal clearanceArea;

    @Column(name = "built_up_area", precision = 10, scale = 2)
    private BigDecimal builtUpArea;

    @Column(name = "land_area", precision = 10, scale = 2)
    private BigDecimal landArea;

    @Column(name = "construction_area", precision = 10, scale = 2)
    private BigDecimal constructionArea;

    // Nhóm Giá bán (đơn vị: tỷ VNĐ)
    @Column(name = "listed_price", precision = 12, scale = 2)
    private BigDecimal listedPrice;

    @Column(name = "loan_price", precision = 12, scale = 2)
    private BigDecimal loanPrice;

    @Column(name = "early_payment_price", precision = 12, scale = 2)
    private BigDecimal earlyPaymentPrice;

    @Column(name = "progress_payment_price", precision = 12, scale = 2)
    private BigDecimal progressPaymentPrice;

    // Nhóm Tài chính & Chính sách
    @Column(name = "supported_banks")
    private String supportedBanks;

    @Column(name = "sales_policy_applied", columnDefinition = "TEXT")
    private String salesPolicyApplied;

    // "CSBH áp dụng" hiển thị dạng ngày (vd 06/06/2026) — tách khỏi salesPolicyApplied (text)
    @Column(name = "sales_policy_date")
    private LocalDate salesPolicyDate;

    @Column(name = "gifts_promotions", columnDefinition = "TEXT")
    private String giftsPromotions;

    // Nhóm Bàn giao & Pháp lý
    @Column(name = "handover_standard", length = 100)
    private String handoverStandard;

    @Column(name = "fund_type", length = 100)
    private String fundType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
