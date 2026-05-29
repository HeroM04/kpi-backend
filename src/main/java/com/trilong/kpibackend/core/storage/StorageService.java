package com.trilong.kpibackend.core.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * StorageService — interface abstraction cho upload file.
 *
 * Hiện tại có 2 implementation:
 * - LocalStorageService: lưu vào filesystem local (dev)
 * - S3StorageService: lưu lên AWS S3 (production)
 *
 * Switch bằng property: app.storage.type=local|s3
 */
public interface StorageService {

    /**
     * Upload file và trả về URL đầy đủ để lưu vào DB.
     * @param file   File cần upload
     * @param subDir Thư mục phân loại: "checkin" | "meeting" | "social"
     * @return URL công khai của file (local URL hoặc S3 URL)
     */
    String uploadFile(MultipartFile file, String subDir) throws Exception;

    /**
     * Xóa file theo URL.
     * @param fileUrl URL đầy đủ của file cần xóa
     * @return true nếu xóa thành công
     */
    boolean deleteFile(String fileUrl);
}
