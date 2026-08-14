package com.trilong.kpibackend.modules.ai.dto;

import lombok.Data;

/**
 * Dữ liệu web gửi lên để nhờ AI chấm điểm bài đăng.
 * Không chứa API key — key nằm ở backend.
 */
@Data
public class ScanPostRequestDTO {

    /** Nội dung (caption) của bài đăng. */
    private String caption;

    /** Link ảnh chụp màn hình bài đăng (tùy chọn). */
    private String screenshotUrl;
}
