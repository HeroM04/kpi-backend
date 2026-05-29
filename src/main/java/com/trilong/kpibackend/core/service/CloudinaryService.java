package com.trilong.kpibackend.core.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * CloudinaryService — Service xử lý upload ảnh lên Cloudinary.
 *
 * <p>Hàm {@link #uploadImage(MultipartFile)} chịu trách nhiệm:
 * <ul>
 *   <li>Validate file đầu vào (không rỗng, đúng định dạng ảnh).</li>
 *   <li>Đẩy ảnh lên Cloudinary với nén tự động chất lượng tốt nhất (auto:good).</li>
 *   <li>Trả về {@code secure_url} (HTTPS) của ảnh sau khi upload.</li>
 * </ul>
 *
 * <p>Hàm {@link #deleteImage(String)} để xoá ảnh cũ theo public_id khi cần thay thế.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    /**
     * Upload một file ảnh lên Cloudinary.
     *
     * @param file File ảnh nhận từ request (MultipartFile)
     * @return secure_url (HTTPS) của ảnh sau khi upload thành công
     * @throws IOException          nếu không đọc được bytes của file
     * @throws IllegalArgumentException nếu file rỗng hoặc không phải ảnh
     */
    @SuppressWarnings("unchecked")
    public String uploadImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File ảnh không được để trống.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Chỉ chấp nhận file ảnh (jpg, png, webp, ...).");
        }

        // Tạo public_id ngẫu nhiên để tránh trùng tên
        String publicId = "kpi-system/" + UUID.randomUUID();

        Map<String, Object> uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "public_id", publicId,
                        "overwrite", true,
                        // Nén ảnh tự động — giảm dung lượng mà vẫn giữ chất lượng tốt
                        "quality", "auto:good",
                        // Tự động format tốt nhất cho từng trình duyệt (webp, avif, ...)
                        "fetch_format", "auto",
                        // Giới hạn kích thước tối đa để tránh ảnh quá lớn
                        "transformation", new com.cloudinary.Transformation().width(1920).crop("limit").generate()
                )
        );

        String secureUrl = (String) uploadResult.get("secure_url");
        log.info("[Cloudinary] Upload thành công: {}", secureUrl);
        return secureUrl;
    }

    /**
     * Xoá ảnh khỏi Cloudinary theo public_id.
     * Dùng khi người dùng thay avatar mới (để dọn ảnh cũ).
     *
     * @param publicId public_id của ảnh cần xoá (lấy từ uploadResult khi upload)
     */
    @SuppressWarnings("unchecked")
    public void deleteImage(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("[Cloudinary] Xoá ảnh thành công: {}", publicId);
        } catch (IOException e) {
            log.warn("[Cloudinary] Không thể xoá ảnh {}: {}", publicId, e.getMessage());
        }
    }
}
