package com.trilong.kpibackend.modules.battle.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitFieldBattleDTO {

    private String customerName;

    private String customerPhone;

    private String project;

    /** MEETING = gặp khách (+10đ), SUPPORT = hỗ trợ khách (+5đ). Trống thì là MEETING. */
    private String battleType;

    @NotBlank(message = "Nội dung thực chiến không được để trống")
    private String content;

    private String photoUrl;

    private String location;

    private Double latitude;

    private Double longitude;
}
