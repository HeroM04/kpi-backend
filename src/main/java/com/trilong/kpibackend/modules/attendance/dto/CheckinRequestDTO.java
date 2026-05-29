package com.trilong.kpibackend.modules.attendance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * CheckinRequestDTO — DTO gửi yêu cầu chấm công từ client.
 *
 * Lưu ý: Trường userId đã được loại bỏ khỏi body để nâng cao tính bảo mật.
 * Hệ thống sẽ tự động trích xuất thông tin người dùng từ JWT token của request.
 */
@Data
public class CheckinRequestDTO {

    @NotNull(message = "Vĩ độ GPS không được để trống")
    private Double latitude;

    @NotNull(message = "Kinh độ GPS không được để trống")
    private Double longitude;

    @NotNull(message = "Ảnh selfie check-in không được để trống")
    private String photoUrl; // URL ảnh sau khi upload lên local/S3

    private String note;

    // CHECK_IN hoặc CHECK_OUT - mặc định là CHECK_IN nếu không truyền
    private String actionType;
}