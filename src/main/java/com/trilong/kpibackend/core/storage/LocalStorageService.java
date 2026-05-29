package com.trilong.kpibackend.core.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * LocalStorageService — lưu file vào filesystem local.
 *
 * Kích hoạt khi: app.storage.type=local (mặc định — dùng khi dev/test)
 * Folder structure: uploads/{subDir}/{year}/{month}/{uuid}.{ext}
 *
 * Serve file qua Spring MVC static resources tại /uploads/**
 * (đã cấu hình trong WebMvcConfig)
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    @Value("${app.storage.local.dir:uploads}")
    private String uploadDir;

    @Value("${app.storage.local.base-url:http://localhost:8080/uploads}")
    private String baseUrl;

    private static final long MAX_SIZE = 20L * 1024 * 1024; // 20MB
    private static final List<String> ALLOWED_TYPES = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic"
    );

    @Override
    public String uploadFile(MultipartFile file, String subDir) throws IOException {
        validateFile(file);

        String ext = getExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + "." + ext;
        LocalDate now = LocalDate.now();
        String datePath = now.getYear() + "/" + String.format("%02d", now.getMonthValue());

        Path targetDir = Paths.get(uploadDir, subDir, datePath);
        Files.createDirectories(targetDir);
        Files.copy(file.getInputStream(), targetDir.resolve(fileName),
                StandardCopyOption.REPLACE_EXISTING);

        return baseUrl + "/" + subDir + "/" + datePath + "/" + fileName;
    }

    @Override
    public boolean deleteFile(String fileUrl) {
        try {
            String relativePath = fileUrl.replace(baseUrl + "/", "");
            Path filePath = Paths.get(uploadDir, relativePath);
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            return false;
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("File ảnh không được để trống");
        if (file.getSize() > MAX_SIZE)
            throw new IllegalArgumentException("File ảnh vượt quá giới hạn 20MB");
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_TYPES.contains(ct.toLowerCase()))
            throw new IllegalArgumentException("Định dạng không hợp lệ. Chấp nhận: JPEG, PNG, WebP, HEIC");
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
