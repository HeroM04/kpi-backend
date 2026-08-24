package com.trilong.kpibackend.modules.training.dto;

import com.trilong.kpibackend.modules.training.entity.TrainingRsvp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/** Câu trả lời tham gia / xin vắng một buổi đào tạo. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingRsvpResponseDTO {
    private Long id;
    private Long sessionId;
    private String sessionTitle;
    private ZonedDateTime sessionStartTime;
    private Long userId;
    private String userFullName;
    private String departmentName;

    /** JOIN = sẽ tham gia · DECLINE = xin vắng. */
    private String choice;
    private String reason;
    /** PENDING · APPROVED · REJECTED — chỉ có ý nghĩa với đơn xin vắng. */
    private String status;
    private Long reviewedBy;
    private String reviewedByFullName;
    private ZonedDateTime reviewedAt;
    private String reviewNote;
    private ZonedDateTime createdAt;

    /**
     * @param nguoiGui thông tin nhân sự gửi, có thể null nếu nơi gọi không cần
     * @param buoiHoc  thông tin buổi đào tạo, có thể null
     */
    public static TrainingRsvpResponseDTO from(TrainingRsvp r,
                                               com.trilong.kpibackend.modules.user.entity.User nguoiGui,
                                               com.trilong.kpibackend.modules.training.entity.TrainingSession buoiHoc) {
        if (r == null) return null;
        return TrainingRsvpResponseDTO.builder()
                .id(r.getId())
                .sessionId(r.getSessionId())
                .sessionTitle(buoiHoc != null ? buoiHoc.getTitle() : null)
                .sessionStartTime(buoiHoc != null ? buoiHoc.getStartTime() : null)
                .userId(r.getUserId())
                .userFullName(nguoiGui != null ? nguoiGui.getFullName() : null)
                .departmentName(nguoiGui != null && nguoiGui.getDepartment() != null
                        ? nguoiGui.getDepartment().getName() : null)
                .choice(r.getChoice())
                .reason(r.getReason())
                .status(r.getStatus())
                .reviewedBy(r.getReviewedBy())
                .reviewedAt(r.getReviewedAt())
                .reviewNote(r.getReviewNote())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
