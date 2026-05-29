package com.trilong.kpibackend.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserDTO {
    private String fullName;
    private String role;
    private String status;
    private Long departmentId;
    private Double basicSalary;
    private String avatarUrl;
    private String password;
}
