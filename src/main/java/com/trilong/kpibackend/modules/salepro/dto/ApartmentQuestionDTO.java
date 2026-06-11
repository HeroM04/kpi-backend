package com.trilong.kpibackend.modules.salepro.dto;

import lombok.Data;

import java.time.ZonedDateTime;

@Data
public class ApartmentQuestionDTO {
    private Long id;
    private Long apartmentId;
    private String fullName;
    private String phone;
    private String content;
    private String answer;
    private String answeredBy;
    private String status;
    private ZonedDateTime createdAt;
}
