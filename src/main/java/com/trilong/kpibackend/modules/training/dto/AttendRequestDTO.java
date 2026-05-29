package com.trilong.kpibackend.modules.training.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AttendRequestDTO {

    @NotBlank(message = "Mã phòng (QR Code) không được để trống")
    private String roomCode;
}
