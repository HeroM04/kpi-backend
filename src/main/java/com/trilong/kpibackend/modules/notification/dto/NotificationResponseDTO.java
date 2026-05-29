package com.trilong.kpibackend.modules.notification.dto;

import com.trilong.kpibackend.modules.notification.entity.Notification;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@Builder
public class NotificationResponseDTO {
    private Long id;
    private String title;
    private String message;
    private String type;
    private String status;
    private ZonedDateTime createdAt;

    public static NotificationResponseDTO from(Notification notification) {
        if (notification == null) return null;
        return NotificationResponseDTO.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .status(notification.getStatus())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
