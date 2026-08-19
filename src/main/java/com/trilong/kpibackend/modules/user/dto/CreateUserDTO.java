package com.trilong.kpibackend.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserDTO {

    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    @NotBlank(message = "Số điện thoại không được để trống")
    private String phoneNumber;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;

    @NotBlank(message = "Vai trò (role) không được để trống")
    private String role;

    private Long departmentId;

    private String avatarUrl;

    private Double basicSalary;

    /** ID người giới thiệu nhân sự này vào công ty — để trống nếu tự ứng tuyển */
    private Long referrerId;

    /** Ngày bắt đầu làm việc — bỏ trống thì lấy ngày tạo tài khoản */
    private java.time.LocalDate joinedDate;
}
