package com.trilong.kpibackend.modules.salepro.dto;

import com.trilong.kpibackend.modules.salepro.entity.ProjectDetails;
import lombok.Data;

@Data
public class ProjectDTO {
    private Long id;
    private String name;
    private String projectType;
    private String status;
    private ProjectDetails details;
    private SalesAgentDTO managingAgent;
    private Long managingAgentId; // dùng khi admin tạo/sửa để gán chuyên viên
}
