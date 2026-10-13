package com.trilong.kpibackend.modules.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitPostDTO {

    @NotBlank(message = "Nền tảng không được để trống")
    private String platform; // Facebook, Zalo, TikTok

    /**
     * Không bắt buộc: story, video xây kênh nhiều khi không có link để dán —
     * ảnh chụp màn hình là bằng chứng chính (xem SocialPostService.submitPost).
     */
    @Size(max = 2000, message = "Link bài đăng quá dài (tối đa 2000 ký tự). "
            + "Hãy sao chép lại link gọn hơn, bỏ bớt phần đuôi sau dấu hỏi.")
    private String link;

    /** Không bắt buộc. */
    private String caption;

    /** Bắt buộc khi nhân sự gửi bài (kiểm ở SocialPostService.submitPost). */
    private String screenshotUrl;

    /** VIDEO = video xây kênh, POST = bài đăng/story. Bỏ trống mặc định POST. */
    private String contentType;
}
