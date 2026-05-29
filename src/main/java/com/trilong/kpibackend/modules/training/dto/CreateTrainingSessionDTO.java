package com.trilong.kpibackend.modules.training.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
public class CreateTrainingSessionDTO {

    @NotBlank(message = "Tiêu đề buổi đào tạo không được để trống")
    private String title;

    private String description;

    private String presenter;

    @NotBlank(message = "Mã phòng không được để trống")
    private String roomCode;

    private ZonedDateTime startTime;

    private String location;

    private Integer maxSlots;

    private String photoUrl;
}
