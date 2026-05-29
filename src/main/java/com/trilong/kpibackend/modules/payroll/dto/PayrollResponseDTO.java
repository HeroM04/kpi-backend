package com.trilong.kpibackend.modules.payroll.dto;

import com.trilong.kpibackend.modules.payroll.entity.Payroll;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollResponseDTO {
    private Long id;
    private Long userId;
    private String fullName;
    private String phoneNumber;
    private String departmentName;
    private String month;
    private Double basicSalary;
    private Double kpiBonus;
    private Double latePenalty;
    private Double netSalary;
    private String status;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public static PayrollResponseDTO from(Payroll payroll) {
        if (payroll == null) return null;
        String deptName = payroll.getUser().getDepartment() != null 
                ? payroll.getUser().getDepartment().getName() 
                : "Không có phòng ban";
        
        return PayrollResponseDTO.builder()
                .id(payroll.getId())
                .userId(payroll.getUser().getId())
                .fullName(payroll.getUser().getFullName())
                .phoneNumber(payroll.getUser().getPhoneNumber())
                .departmentName(deptName)
                .month(payroll.getMonth())
                .basicSalary(payroll.getBasicSalary())
                .kpiBonus(payroll.getKpiBonus())
                .latePenalty(payroll.getLatePenalty())
                .netSalary(payroll.getNetSalary())
                .status(payroll.getStatus())
                .createdAt(payroll.getCreatedAt())
                .updatedAt(payroll.getUpdatedAt())
                .build();
    }
}
