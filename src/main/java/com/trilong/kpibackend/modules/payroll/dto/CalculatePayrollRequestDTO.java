package com.trilong.kpibackend.modules.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculatePayrollRequestDTO {

    @NotBlank(message = "Tháng tính lương không được để trống")
    @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "Tháng tính lương phải có định dạng YYYY-MM")
    private String month;

    private Long userId;
}
