package com.trilong.kpibackend.modules.salepro.dto;

import lombok.Data;

@Data
public class BuildingDTO {
    private Long id;
    private Long projectId;
    private String buildingName;
    private String subdivisionName;
    private Integer totalFloors;
}
