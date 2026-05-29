package com.trilong.kpibackend.modules.post.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitPostDTO {

    @NotBlank(message = "Nền tảng không được để trống")
    private String platform; // Facebook, Zalo, TikTok

    @NotBlank(message = "Link bài đăng không được để trống")
    private String link;

    private String caption;

    private String screenshotUrl;
}
