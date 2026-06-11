package com.trilong.kpibackend.modules.salepro.dto;

import lombok.Data;

@Data
public class SalesAgentDTO {
    private Long id;
    private String fullName;
    private String title;
    private String phone;
    private String email;
    private String avatarUrl;
    private String zaloLink;
}
