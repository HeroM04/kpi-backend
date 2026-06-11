package com.trilong.kpibackend.modules.event.dto;

import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;

@Data
public class EventDTO {
    private Long id;
    private String title;
    private String slug;
    private String eventType;
    private String status;
    private String bannerImage;
    private String description;
    private String location;
    private ZonedDateTime startTime;
    private ZonedDateTime endTime;
    private Long projectId;
    private List<String> galleryImages;
    private Integer participantCount;
    private Integer checkinCount;
}
