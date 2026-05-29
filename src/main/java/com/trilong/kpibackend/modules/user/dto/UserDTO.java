package com.trilong.kpibackend.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String fullName;
    private String phoneNumber;
    private String role;
    private String status;
    private String avatarUrl;
    private Double basicSalary;
    private DepartmentDTO department;
    private ZonedDateTime createdAt;
}
