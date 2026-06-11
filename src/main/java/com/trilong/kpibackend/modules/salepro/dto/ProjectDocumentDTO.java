package com.trilong.kpibackend.modules.salepro.dto;

import lombok.Data;

@Data
public class ProjectDocumentDTO {
    private Long id;
    private Long projectId;
    private String label;
    private String driveUrl;
    private String docType;
    private Integer sortOrder;
}
