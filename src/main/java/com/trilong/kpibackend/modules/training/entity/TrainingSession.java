package com.trilong.kpibackend.modules.training.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

@Entity
@Table(name = "training_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String presenter;

    @Column(name = "room_code", unique = true, length = 100)
    private String roomCode;

    /**
     * Loại đào tạo:
     * <ul>
     *   <li>{@code SKILL} — đào tạo kỹ năng. Học một buổi trong nhóm kỹ năng là
     *       coi như xong cả nhóm, các buổi còn lại tự điểm danh.</li>
     *   <li>{@code PROJECT} — đào tạo dự án.</li>
     * </ul>
     * Buổi tạo từ trước khi có phân loại thì mặc định là kỹ năng.
     */
    @Column(name = "training_type", length = 20)
    @Builder.Default
    private String trainingType = "SKILL";

    /**
     * Mã nhóm kỹ năng, ví dụ {@code CHOT_SALE}.
     *
     * <p>Một kỹ năng thường dạy lặp lại nhiều buổi cho nhiều ca khác nhau. Các
     * buổi cùng dạy một kỹ năng thì đặt chung một mã ở đây; nhân sự dự một buổi
     * bất kỳ trong nhóm là xong nhóm đó, không phải học lại ở những buổi sau.
     *
     * <p>Để trống nghĩa là buổi đứng riêng, không thuộc nhóm nào.
     */
    @Column(name = "skill_group", length = 60)
    private String skillGroup;

    @Column(name = "start_time")
    private ZonedDateTime startTime;

    /**
     * Buổi học kéo dài bao nhiêu phút — để biết lúc nào chuyển từ ĐANG DIỄN RA
     * sang ĐÃ KẾT THÚC. Không điền thì mặc định 2 tiếng.
     */
    @Column(name = "duration_minutes")
    @Builder.Default
    private Integer durationMinutes = 120;

    private String location;

    @Column(name = "max_slots")
    @Builder.Default
    private Integer maxSlots = 20;

    @Column(length = 50)
    @Builder.Default
    private String status = "UPCOMING"; // UPCOMING, COMPLETED, CANCELLED

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    /**
     * Link video YouTube của buổi đào tạo.
     * Admin cập nhật sau khi buổi học kết thúc (status = COMPLETED).
     * Mobile App dùng url_launcher để mở YouTube App hoặc Browser.
     */
    @Column(name = "video_url", columnDefinition = "TEXT")
    private String videoUrl;

    @PrePersist
    public void prePersist() {
        if (this.status == null) this.status = "UPCOMING";
        if (this.maxSlots == null) this.maxSlots = 20;
        if (this.durationMinutes == null) this.durationMinutes = 120;
        if (this.trainingType == null) this.trainingType = "SKILL";
    }

    /** Buổi này có thuộc một nhóm kỹ năng để áp quy tắc học một lần không. */
    public boolean coNhomKyNang() {
        return "SKILL".equals(trainingType == null ? "SKILL" : trainingType)
                && skillGroup != null && !skillGroup.isBlank();
    }

    /** Thời điểm buổi học kết thúc theo lịch. */
    public ZonedDateTime getEndTime() {
        if (startTime == null) return null;
        int phut = (durationMinutes == null || durationMinutes <= 0) ? 120 : durationMinutes;
        return startTime.plusMinutes(phut);
    }

    /**
     * Trạng thái để HIỂN THỊ, tính lại theo đồng hồ mỗi lần đọc.
     *
     * <p>Đến giờ học là buổi tự chuyển sang ĐANG DIỄN RA mà không cần ai bấm gì
     * và không phụ thuộc vào tác vụ nền — máy chủ gói miễn phí hay ngủ, chờ tác
     * vụ nền đổi trạng thái thì có lúc cả buổi học trôi qua vẫn ghi "sắp diễn ra".
     *
     * <p>Hai trạng thái do Admin đặt tay thì giữ nguyên: đã HỦY, và đã KẾT THÚC
     * khi Admin chủ động đóng sớm.
     *
     * @return CANCELLED · COMPLETED · ONGOING · UPCOMING
     */
    public String getDisplayStatus() {
        if ("CANCELLED".equals(status)) return "CANCELLED";
        if ("COMPLETED".equals(status)) return "COMPLETED";
        if (startTime == null) return "UPCOMING";

        ZonedDateTime bayGio = ZonedDateTime.now(startTime.getZone());
        if (bayGio.isBefore(startTime)) return "UPCOMING";
        return bayGio.isBefore(getEndTime()) ? "ONGOING" : "COMPLETED";
    }
}
