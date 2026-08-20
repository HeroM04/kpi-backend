package com.trilong.kpibackend.modules.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitPostDTO {

    @NotBlank(message = "Nền tảng không được để trống")
    private String platform; // Facebook, Zalo, TikTok

    @NotBlank(message = "Link bài đăng không được để trống")
    @Size(max = 2000, message = "Link bài đăng quá dài (tối đa 2000 ký tự). "
            + "Hãy sao chép lại link gọn hơn, bỏ bớt phần đuôi sau dấu hỏi.")
    private String link;

    private String caption;

    private String screenshotUrl;

    /** VIDEO = video xây kênh, POST = bài đăng/story. Bỏ trống mặc định POST. */
    private String contentType;
}
