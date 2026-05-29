package com.trilong.kpibackend.modules.upload.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/v1/upload")
@Tag(name = "Upload", description = "Quản lý upload file lên Cloud (S3)")
@SecurityRequirement(name = "Bearer Authentication")
public class UploadController {

    // Giả lập service S3. Thực tế bạn sẽ tiêm AmazonS3 client vào đây
    // private final AmazonS3 s3Client; 

    @Value("${app.storage.local.dir:uploads}")
    private String uploadDir;

    @Value("${app.storage.local.base-url:http://localhost:8080/uploads}")
    private String localBaseUrl;

    @Operation(
            summary = "Upload File (Local)",
            description = "Lưu file vật lý vào thư mục uploads của server và trả về URL."
    )
    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadFileLocal(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) throw new RuntimeException("File is empty");
            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.jpg");
            String fileName = UUID.randomUUID().toString() + "_" + originalFilename;
            
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);
            Path filePath = uploadPath.resolve(fileName);
            file.transferTo(filePath.toFile());
            
            String publicUrl = localBaseUrl + "/" + fileName;
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", Map.of("url", publicUrl)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
    
    @Operation(
            summary = "Lấy URL Upload (Presigned URL)",
            description = "Trả về một Presigned URL từ AWS S3 để Mobile/Web có thể đẩy ảnh lên trực tiếp mà không cần gửi qua Server (Giảm tải băng thông Server). URL có hạn trong 15 phút."
    )
    @GetMapping("/presigned-url")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getPresignedUrl(
            @RequestParam String fileName,
            @RequestParam String contentType) {
        try {
            // MÔ PHỎNG logic sinh Presigned URL
            String fileKey = "uploads/" + UUID.randomUUID().toString() + "-" + fileName;
            
            // Generate presigned URL (Ví dụ: 15 phút hết hạn)
            // URL url = s3Client.generatePresignedUrl(bucketName, fileKey, expiration, HttpMethod.PUT);
            
            String dummyPresignedUrl = "https://s3.amazonaws.com/kpi-bucket/" + fileKey + "?X-Amz-Signature=dummy-signature";
            String publicUrl = "https://kpi-bucket.s3.amazonaws.com/" + fileKey;

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "data", Map.of(
                            "presignedUrl", dummyPresignedUrl,
                            "publicUrl", publicUrl,
                            "fileKey", fileKey,
                            "expiresIn", 900 // 15 phút
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
}
