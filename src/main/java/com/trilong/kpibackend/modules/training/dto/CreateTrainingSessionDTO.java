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

    /** Buổi học kéo dài bao nhiêu phút. Trống thì mặc định 120. */
    private Integer durationMinutes;

    /** SKILL = đào tạo kỹ năng (mặc định) · PROJECT = đào tạo dự án. */
    private String trainingType;

    /**
     * Mã nhóm kỹ năng — các buổi dạy cùng một kỹ năng đặt chung mã này.
     * Học một buổi trong nhóm là xong cả nhóm.
     */
    private String skillGroup;

    private String photoUrl;

    /**
     * Link video YouTube (Admin cập nhật sau khi buổi học kết thúc).
     * Ví dụ: https://www.youtube.com/watch?v=dQw4w9WgXcQ
     */
    private String videoUrl;

    /**
     * Trạng thái buổi đào tạo: UPCOMING, ONGOING, COMPLETED, CANCELLED
     * Admin có thể thay đổi khi cập nhật thông tin buổi học.
     */
    private String status;
}
