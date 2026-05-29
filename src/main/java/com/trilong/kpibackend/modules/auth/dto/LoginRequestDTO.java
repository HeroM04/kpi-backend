package com.trilong.kpibackend.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO nhận dữ liệu đăng nhập từ Mobile App.
 * Login bằng số điện thoại + mật khẩu.
 */
@Data
public class LoginRequestDTO {

    @NotBlank(message = "Số điện thoại không được để trống")
    private String phoneNumber;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;
}
