package com.trilong.kpibackend.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO cập nhật ảnh đại diện gốc.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAvatarRequestDTO {

    @NotBlank(message = "Đường dẫn ảnh đại diện không được để trống")
    private String avatarUrl;
}
