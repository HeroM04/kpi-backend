package com.trilong.kpibackend.modules.training.dto;

import com.trilong.kpibackend.modules.training.entity.TrainingAttendee;
import com.trilong.kpibackend.modules.training.entity.TrainingRsvp;
import com.trilong.kpibackend.modules.training.entity.TrainingSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingAttendeeResponseDTO {
    private Long userId;
    private String fullName;
    private String role;
    private String departmentName;
    private ZonedDateTime attendedAt;

    /** Điểm danh do hệ thống tự ghi, không phải người này ngồi học buổi này. */
    private Boolean autoMarked;

    /**
     * REAL = có mặt thật · SKILL_GROUP = đã học nhóm kỹ năng · EXEMPT = được miễn
     * (ghi chú vắng đã duyệt) · NOTE = chưa được tính, chỉ mới có ghi chú vắng
     * (chờ duyệt hoặc bị từ chối — chỉ có trong lịch sử khi hỏi kèm ghi chú).
     */
    private String source;

    // ── Buổi đào tạo của dòng này — để lịch sử hiện đúng "buổi gì" ──────────
    private Long sessionId;
    private String sessionTitle;
    private String presenter;
    /** SKILL = đào tạo kỹ năng · PROJECT = đào tạo dự án. */
    private String trainingType;
    private ZonedDateTime sessionStartTime;

    // ── Ghi chú vắng nhân sự gửi cho buổi này (nếu có) ───────────────────────
    private String note;
    /** PENDING · APPROVED · REJECTED. */
    private String noteStatus;
    /** Admin ghi khi duyệt / từ chối. */
    private String noteReviewNote;

    public static TrainingAttendeeResponseDTO from(TrainingAttendee attendee) {
        if (attendee == null) return null;
        // Dòng điểm danh tự động được tạo mà không gán sẵn quan hệ user, nên
        // phải đọc phòng hờ null thay vì gọi thẳng getUser().getId().
        var u = attendee.getUser();
        return TrainingAttendeeResponseDTO.builder()
                .autoMarked(attendee.laTuDong())
                .source(attendee.getSource() != null ? attendee.getSource() : "REAL")
                .userId(u != null ? u.getId() : attendee.getUserId())
                .fullName(u != null ? u.getFullName() : null)
                .role(u != null ? u.getRole() : null)
                .departmentName(u != null && u.getDepartment() != null ? u.getDepartment().getName() : null)
                .attendedAt(attendee.getAttendedAt())
                .sessionId(attendee.getSessionId())
                .build();
    }

    /** Ghép thông tin buổi học (buổi đã bị xoá thì để trống). */
    public TrainingAttendeeResponseDTO kemBuoi(TrainingSession s) {
        if (s == null) return this;
        this.sessionId = s.getId();
        this.sessionTitle = s.getTitle();
        this.presenter = s.getPresenter();
        this.trainingType = s.getTrainingType() != null ? s.getTrainingType() : "SKILL";
        this.sessionStartTime = s.getStartTime();
        return this;
    }

    /** Ghép ghi chú vắng của chính người này cho buổi đó. */
    public TrainingAttendeeResponseDTO kemGhiChu(TrainingRsvp r) {
        if (r == null || !"DECLINE".equals(r.getChoice())) return this;
        this.note = r.getReason();
        this.noteStatus = r.getStatus();
        this.noteReviewNote = r.getReviewNote();
        return this;
    }
}
