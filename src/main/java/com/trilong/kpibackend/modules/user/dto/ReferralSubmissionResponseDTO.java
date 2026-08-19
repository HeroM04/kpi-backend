package com.trilong.kpibackend.modules.user.dto;

import com.trilong.kpibackend.modules.user.entity.ReferralSubmission;
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
public class ReferralSubmissionResponseDTO {
    private Long id;
    private Long referrerId;
    private String referrerFullName;
    private String referrerDepartmentName;
    private String candidateName;
    private String candidatePhone;
    private String note;
    private String status;
    private ZonedDateTime submittedAt;
    private Long reviewedBy;
    private String reviewedByFullName;
    private ZonedDateTime reviewedAt;
    private String reviewNote;

    /** Tài khoản được tạo khi duyệt */
    private Long createdUserId;
    private LocalDate joinedDate;

    /** Ngày người giới thiệu được cộng 15đ (tròn một tháng kể từ ngày vào làm) */
    private LocalDate rewardDate;

    /** Đã thực sự cộng 15đ cho người giới thiệu hay chưa */
    private Boolean rewardGranted;

    public static ReferralSubmissionResponseDTO from(ReferralSubmission r,
                                                     String referrerFullName,
                                                     String referrerDepartmentName,
                                                     String reviewerFullName,
                                                     boolean rewardGranted) {
        if (r == null) return null;
        return ReferralSubmissionResponseDTO.builder()
                .id(r.getId())
                .referrerId(r.getReferrerId())
                .referrerFullName(referrerFullName)
                .referrerDepartmentName(referrerDepartmentName)
                .candidateName(r.getCandidateName())
                .candidatePhone(r.getCandidatePhone())
                .note(r.getNote())
                .status(r.getStatus())
                .submittedAt(r.getSubmittedAt())
                .reviewedBy(r.getReviewedBy())
                .reviewedByFullName(reviewerFullName)
                .reviewedAt(r.getReviewedAt())
                .reviewNote(r.getReviewNote())
                .createdUserId(r.getCreatedUserId())
                .joinedDate(r.getJoinedDate())
                .rewardDate(r.getJoinedDate() == null ? null : r.getJoinedDate().plusMonths(1))
                .rewardGranted(rewardGranted)
                .build();
    }
}
