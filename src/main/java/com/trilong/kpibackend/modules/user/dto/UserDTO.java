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

    /** Người giới thiệu nhân sự này vào công ty (null nếu tự ứng tuyển) */
    private Long referrerId;
    private String referrerFullName;

    /** Ngày bắt đầu làm việc — mốc tính tròn một tháng cho điểm giới thiệu */
    private java.time.LocalDate joinedDate;
}
