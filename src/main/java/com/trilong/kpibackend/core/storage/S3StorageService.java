package com.trilong.kpibackend.core.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.LocalDate;
import java.util.UUID;

/**
 * S3StorageService — upload file lên AWS S3.
 *
 * Kích hoạt khi: app.storage.type=s3
 *
 * File URL format: https://{bucket}.s3.{region}.amazonaws.com/{key}
 * hoặc custom domain: https://cdn.trilong.vn/{key}
 *
 * Cài đặt production:
 * 1. Tạo S3 bucket với public read policy (hoặc dùng presigned URL)
 * 2. Tạo IAM user với quyền s3:PutObject, s3:DeleteObject
 * 3. Set env vars: AWS_ACCESS_KEY, AWS_SECRET_KEY, AWS_BUCKET_NAME
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.base-url}")
    private String baseUrl;

    public S3StorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public String uploadFile(MultipartFile file, String subDir) throws Exception {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("File ảnh không được để trống");

        // Tạo S3 key với cấu trúc rõ ràng
        LocalDate now = LocalDate.now();
        String ext = getExtension(file.getOriginalFilename());
        String key = String.format("%s/%d/%02d/%s.%s",
                subDir, now.getYear(), now.getMonthValue(), UUID.randomUUID(), ext);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                // Public read — đổi sang presigned URL nếu cần private
                .build();

        s3Client.putObject(putRequest, RequestBody.fromBytes(file.getBytes()));

        return baseUrl + "/" + key;
    }

    @Override
    public boolean deleteFile(String fileUrl) {
        try {
            String key = fileUrl.replace(baseUrl + "/", "");
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
