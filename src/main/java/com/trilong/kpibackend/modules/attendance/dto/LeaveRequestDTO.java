package com.trilong.kpibackend.modules.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/** Nhân sự gửi đơn xin vắng từ màn hình Điểm danh trên app. */
@Data
public class LeaveRequestDTO {

    @NotNull(message = "Vui lòng chọn ngày xin vắng")
    private LocalDate leaveDate;

    @NotBlank(message = "Vui lòng nhập lý do xin vắng")
    private String reason;
}
