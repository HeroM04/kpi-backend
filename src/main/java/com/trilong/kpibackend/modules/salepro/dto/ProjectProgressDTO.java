package com.trilong.kpibackend.modules.salepro.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ProjectProgressDTO {
    private Long id;
    private Long projectId;
    private String title;
    private LocalDate progressDate;
    private String externalUrl;
    private List<String> images;
    private Integer sortOrder;
}
