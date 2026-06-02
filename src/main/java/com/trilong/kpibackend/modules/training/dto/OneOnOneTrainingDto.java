package com.trilong.kpibackend.modules.training.dto;

import lombok.Data;
import java.time.ZonedDateTime;

@Data
public class OneOnOneTrainingDto {
    private Long id;
    private Long userId;
    private String userName;
    private String userAvatar;
    private String content;
    private String photoUrl;
    private String status;
    private ZonedDateTime submittedAt;
}
