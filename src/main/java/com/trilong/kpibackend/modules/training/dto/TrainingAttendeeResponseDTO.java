package com.trilong.kpibackend.modules.training.dto;

import com.trilong.kpibackend.modules.training.entity.TrainingAttendee;
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

    /** REAL = có mặt thật · SKILL_GROUP = đã học nhóm kỹ năng · EXEMPT = được miễn. */
    private String source;

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
                .build();
    }
}
