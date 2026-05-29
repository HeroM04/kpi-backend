package com.trilong.kpibackend.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** DTO dùng để đổi access token mới từ refresh token. */
@Data
public class RefreshTokenRequestDTO {
    @NotBlank(message = "Refresh token không được để trống")
    private String refreshToken;
}
