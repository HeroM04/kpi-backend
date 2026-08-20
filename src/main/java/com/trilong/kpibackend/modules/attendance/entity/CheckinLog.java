package com.trilong.kpibackend.modules.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;

/**
 * CheckinLog — Lưu thông tin mỗi lần chấm công.
 *
 * <p>Luồng nghiệp vụ:
 * <ul>
 *   <li>GPS <= 50m so với văn phòng → {@code status = APPROVED}, {@code checkinType = OFFICE}</li>
 *   <li>GPS > 50m → {@code status = PENDING}, {@code checkinType = FIELD}, cần Admin duyệt</li>
 *   <li>Mốc giờ: check-in trước 08:30 = đúng giờ, check-in sau 08:30 = muộn</li>
 * </ul>
 */
@Entity
@Table(
    name = "checkin_logs",
    // Bảng này lớn nhất hệ thống: 200 nhân sự chấm công vào/ra mỗi ngày là hơn
    // 100.000 dòng một năm. Không có chỉ mục thì mỗi lần lọc phải quét sạch cả
    // bảng. Ba chỉ mục dưới đây phủ đúng ba cách tra cứu hay dùng: xem theo
    // ngày mới nhất, xem lịch sử một người, và lọc theo trạng thái chờ duyệt.
    indexes = {
        @Index(name = "idx_checkin_time", columnList = "checkin_time DESC"),
        @Index(name = "idx_checkin_user_time", columnList = "user_id, checkin_time DESC"),
        @Index(name = "idx_checkin_status", columnList = "status")
    }
)
@Data
@NoArgsConstructor
public class CheckinLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** userId lấy từ JWT token — không cho client tự truyền */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Thời gian chấm công (server tự ghi, client không tự đặt được) */
    @Column(name = "checkin_time")
    private ZonedDateTime checkinTime;

    /** OFFICE (trong 50m) hoặc FIELD (ngoài 50m) */
    @Column(name = "checkin_type", length = 20)
    private String checkinType;

    /** CHECK_IN hoặc CHECK_OUT */
    @Column(name = "action_type", length = 20)
    private String actionType;

    /** Tọa độ GPS gốc từ thiết bị (đã kiểm tra không phải mock) */
    private Double latitude;
    private Double longitude;

    /** Khoảng cách tính được từ tọa độ đến văn phòng (đơn vị: mét) */
    @Column(name = "distance_to_office")
    private Double distanceToOffice;

    /**
     * Địa chỉ thực tế từ Reverse Geocoding (ví dụ: "123 Tôn Đức Thắng, Đống Đa, Hà Nội").
     * Client thực hiện geocoding và truyền lên — đây chỉ là trường lưu trữ.
     */
    @Column(name = "address", length = 500)
    private String address;

    /**
     * URL ảnh selfie có watermark (timestamp + địa chỉ) đã upload lên Cloudinary.
     * Ảnh phải qua ML Kit face detection trước khi upload (client-side).
     */
    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    /** Lý do chấm công ngoại tuyến — bắt buộc khi checkinType = FIELD */
    @Column(columnDefinition = "TEXT")
    private String note;

    /** APPROVED (tự động) | PENDING (chờ duyệt) | REJECTED (từ chối) */
    @Column(length = 20)
    private String status;

    /** Lý do Admin từ chối (nếu status = REJECTED) */
    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @PrePersist
    public void prePersist() {
        if (this.checkinTime == null) {
            this.checkinTime = ZonedDateTime.now();
        }
        if (this.actionType == null) {
            this.actionType = "CHECK_IN";
        }
    }
}