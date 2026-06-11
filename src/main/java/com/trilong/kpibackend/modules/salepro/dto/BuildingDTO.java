package com.trilong.kpibackend.modules.salepro.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BuildingDTO {
    private Long id;
    private Long projectId;
    private String buildingName;
    private String subdivisionName;
    private Integer totalFloors;
    private long apartmentCount;   // tổng số căn của tòa (thực tế)
    private long availableCount;   // số căn còn hàng (CON_HANG)

    // ===== Tổng quan tòa =====
    private String ownershipType;
    private String buildingHandoverStandard;
    private BigDecimal totalArea;
    private Integer totalApartments;   // số căn công bố
    private Integer elevatorCount;
    private String description;
    private String imageUrl;
    private Integer constructionProgress;
    private String salesPolicy;

    // ===== Marker masterplan =====
    private Double markerLat;
    private Double markerLng;

    // ===== Layout tòa (mặt bằng tầng) =====
    private List<BuildingFloorPlanDTO> floorPlans;
}
