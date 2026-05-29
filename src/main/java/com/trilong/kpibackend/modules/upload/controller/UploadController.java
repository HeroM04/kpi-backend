package com.trilong.kpibackend.modules.upload.controller;

import com.trilong.kpibackend.core.service.CloudinaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * UploadController — API upload file ảnh lên Cloudinary.
 *
 * <p>Endpoint {@code POST /api/v1/upload/image} nhận file từ client,
 * đẩy lên Cloudinary và trả về {@code secure_url} để lưu vào DB.
 *
 * <p>Flow:
 * <pre>
 *   Client ──(multipart/form-data)──► UploadController
 *       ──► CloudinaryService.uploadImage()
 *       ──► Cloudinary CDN
 *       ◄── secure_url (HTTPS)
 *   Client ◄── { "url": "https://res.cloudinary.com/..." }
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
@Tag(name = "Upload", description = "Upload file ảnh lên Cloudinary CDN")
@SecurityRequirement(name = "Bearer Authentication")
public class UploadController {

    private final CloudinaryService cloudinaryService;

    /**
     * Upload một file ảnh lên Cloudinary.
     *
     * @param file File ảnh (jpg, png, webp, ...) — max 20MB (cấu hình trong application.properties)
     * @return JSON chứa {@code url} — đường link HTTPS đến ảnh trên Cloudinary
     */
    @Operation(
            summary = "Upload ảnh lên Cloudinary",
            description = "Nhận file ảnh, đẩy lên Cloudinary với nén tự động, trả về secure_url để lưu vào DB."
    )
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            String url = cloudinaryService.uploadImage(file);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "data", Map.of("url", url)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "message", "Upload thất bại: " + e.getMessage()
            ));
        }
    }
}
