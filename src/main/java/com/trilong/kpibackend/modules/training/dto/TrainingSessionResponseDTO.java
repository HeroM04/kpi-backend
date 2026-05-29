package com.trilong.kpibackend.modules.training.dto;

import com.trilong.kpibackend.modules.training.entity.TrainingSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingSessionResponseDTO {
    private Long id;
    private String title;
    private String description;
    private String presenter;
    private String roomCode;
    private ZonedDateTime startTime;
    private String location;
    private Integer maxSlots;
    private String status;
    private String photoUrl;
    private long currentSlots;
    private List<TrainingAttendeeResponseDTO> attendees;

    public static TrainingSessionResponseDTO from(TrainingSession session, long currentSlots) {
        if (session == null) return null;
        return TrainingSessionResponseDTO.builder()
                .id(session.getId())
                .title(session.getTitle())
                .description(session.getDescription())
                .presenter(session.getPresenter())
                .roomCode(session.getRoomCode())
                .startTime(session.getStartTime())
                .location(session.getLocation())
                .maxSlots(session.getMaxSlots())
                .status(session.getStatus())
                .photoUrl(session.getPhotoUrl())
                .currentSlots(currentSlots)
                .build();
    }
}
