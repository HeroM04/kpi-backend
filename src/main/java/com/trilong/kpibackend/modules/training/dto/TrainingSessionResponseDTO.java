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
    private ZonedDateTime endTime;
    private Integer durationMinutes;
    private String location;
    private Integer maxSlots;
    /** Trạng thái Admin đã đặt — dùng cho form sửa trên Web Admin. */
    private String status;
    /**
     * Trạng thái để hiển thị, tính theo đồng hồ: ONGOING · UPCOMING · COMPLETED
     * · CANCELLED. Đây là giá trị ứng dụng nên dùng để vẽ nhãn và sắp thứ tự.
     */
    private String displayStatus;
    private String photoUrl;
    private String videoUrl;
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
                .endTime(session.getEndTime())
                .durationMinutes(session.getDurationMinutes())
                .location(session.getLocation())
                .maxSlots(session.getMaxSlots())
                .status(session.getStatus())
                .displayStatus(session.getDisplayStatus())
                .photoUrl(session.getPhotoUrl())
                .videoUrl(session.getVideoUrl())
                .currentSlots(currentSlots)
                .build();
    }
}
