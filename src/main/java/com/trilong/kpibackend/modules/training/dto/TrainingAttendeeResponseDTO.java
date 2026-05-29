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

    public static TrainingAttendeeResponseDTO from(TrainingAttendee attendee) {
        if (attendee == null) return null;
        return TrainingAttendeeResponseDTO.builder()
                .userId(attendee.getUser().getId())
                .fullName(attendee.getUser().getFullName())
                .role(attendee.getUser().getRole())
                .departmentName(attendee.getUser().getDepartment() != null ? attendee.getUser().getDepartment().getName() : null)
                .attendedAt(attendee.getAttendedAt())
                .build();
    }
}
