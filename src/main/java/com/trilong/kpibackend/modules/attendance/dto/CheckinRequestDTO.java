package com.trilong.kpibackend.modules.attendance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * CheckinRequestDTO — DTO gửi yêu cầu chấm công từ Mobile App.
 *
 * <p><b>Luồng nghiệp vụ:</b>
 * <ol>
 *   <li>Mobile App lấy GPS → kiểm tra mock location → nếu mock thì block</li>
 *   <li>Mở camera (block gallery) → chụp ảnh → ML Kit face detection (offline) → phải có ít nhất 1 khuôn mặt</li>
 *   <li>Gọi Reverse Geocoding (Apple/Google native) → lấy địa chỉ → vẽ watermark lên ảnh</li>
 *   <li>Upload ảnh đã watermark lên Cloudinary → lấy {@code photoUrl}</li>
 *   <li>Gửi request này lên backend với đầy đủ thông tin</li>
 * </ol>
 *
 * <p><b>Bảo mật:</b> {@code userId} KHÔNG có trong DTO — server tự lấy từ JWT token.
 */
@Data
public class CheckinRequestDTO {

    @NotNull(message = "Vĩ độ GPS không được để trống")
    private Double latitude;

    @NotNull(message = "Kinh độ GPS không được để trống")
    private Double longitude;

    /**
     * Địa chỉ thực tế từ Reverse Geocoding (ví dụ: "123 Tôn Đức Thắng, Đống Đa, Hà Nội").
     * Mobile App thực hiện geocoding rồi truyền chuỗi địa chỉ này lên.
     * Được dùng để vẽ watermark lên ảnh và lưu vào DB.
     */
    private String address;

    /**
     * URL ảnh selfie đã watermark, đã upload lên Cloudinary.
     * Ảnh phải qua ML Kit face detection (ít nhất 1 khuôn mặt) trước khi upload.
     */
    @NotNull(message = "Ảnh selfie check-in không được để trống")
    private String photoUrl;

    /**
     * Lý do chấm công ngoại tuyến — BẮT BUỘC khi GPS > 50m so với văn phòng.
     * Backend sẽ validate: nếu checkinType = FIELD mà note = null/empty thì báo lỗi.
     */
    private String note;

    /** CHECK_IN hoặc CHECK_OUT — mặc định CHECK_IN nếu không truyền */
    private String actionType;
}