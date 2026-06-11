package com.trilong.kpibackend.modules.salepro.dto;

import lombok.Data;

@Data
public class BuildingFloorPlanDTO {
    private Long id;
    private Long buildingId;
    private String floorLabel;
    private String imageUrl;
    private String note;
    private Integer sortOrder;
}
