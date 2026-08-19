package com.trilong.kpibackend.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Nhân sự gửi đơn giới thiệu ứng viên từ app. */
@Data
public class ReferralSubmissionDTO {

    @NotBlank(message = "Vui lòng nhập họ tên người được giới thiệu")
    private String candidateName;

    @NotBlank(message = "Vui lòng nhập số điện thoại người được giới thiệu")
    @Pattern(regexp = "^0\\d{8,10}$", message = "Số điện thoại không hợp lệ")
    private String candidatePhone;

    private String note;
}
