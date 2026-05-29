package com.trilong.kpibackend.core.utils;

import com.trilong.kpibackend.core.storage.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Tiện ích upload và quản lý ảnh cho hệ thống KPI.
 *
 * Delegate thực tế cho StorageService để tự động switch giữa Local Storage
 * và AWS S3 Cloud Storage tùy theo cấu hình môi trường.
 */
@Component
public class ImageUploadUtils {

    @Autowired
    private StorageService storageService;

    /**
     * Upload ảnh và trả về URL truy cập công khai.
     *
     * @param file     File ảnh từ request
     * @param subDir   Thư mục con phân loại: "checkin", "meeting", "social"
     * @return URL đầy đủ để lưu vào DB
     */
    public String uploadImage(MultipartFile file, String subDir) throws IOException {
        try {
            return storageService.uploadFile(file, subDir);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Lỗi upload ảnh lên server: " + e.getMessage(), e);
        }
    }

    /**
     * Upload ảnh check-in chấm công.
     */
    public String uploadCheckinPhoto(MultipartFile file) throws IOException {
        return uploadImage(file, "checkin");
    }

    /**
     * Upload ảnh gặp khách (TC2 - Thực chiến).
     */
    public String uploadMeetingPhoto(MultipartFile file) throws IOException {
        return uploadImage(file, "meeting");
    }

    /**
     * Upload ảnh chụp màn hình bài đăng mạng xã hội (TC3 - Lan tỏa).
     */
    public String uploadSocialScreenshot(MultipartFile file) throws IOException {
        return uploadImage(file, "social");
    }

    /**
     * Xóa file ảnh theo URL.
     */
    public boolean deleteByUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return false;
        }
        return storageService.deleteFile(fileUrl);
    }
}
