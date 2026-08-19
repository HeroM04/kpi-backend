package com.trilong.kpibackend.modules.attendance.dto;

import com.trilong.kpibackend.modules.attendance.entity.LeaveRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveRequestResponseDTO {
    private Long id;
    private Long userId;
    private String userFullName;
    private String departmentName;
    private LocalDate leaveDate;
    private String reason;
    private String status;
    private ZonedDateTime submittedAt;
    private Long reviewedBy;
    private String reviewedByFullName;
    private ZonedDateTime reviewedAt;
    private String reviewNote;
    /** Điểm KPI đã áp cho ngày này (-10 nếu có phép, 0 nếu chưa duyệt) */
    private Integer kpiPoints;

    public static LeaveRequestResponseDTO from(LeaveRequest r, String userFullName,
                                               String departmentName, String reviewerFullName,
                                               int approvedPenalty) {
        if (r == null) return null;
        return LeaveRequestResponseDTO.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .userFullName(userFullName)
                .departmentName(departmentName)
                .leaveDate(r.getLeaveDate())
                .reason(r.getReason())
                .status(r.getStatus())
                .submittedAt(r.getSubmittedAt())
                .reviewedBy(r.getReviewedBy())
                .reviewedByFullName(reviewerFullName)
                .reviewedAt(r.getReviewedAt())
                .reviewNote(r.getReviewNote())
                .kpiPoints(Boolean.TRUE.equals(r.getKpiApplied()) ? approvedPenalty : 0)
                .build();
    }
}
