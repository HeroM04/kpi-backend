package com.trilong.kpibackend.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

import java.io.InputStream;
import java.net.URL;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceRecognitionService {

    private final RekognitionClient rekognitionClient;

    @Value("${aws.rekognition.similarity-threshold:60.0}")
    private Float similarityThreshold;

    /**
     * So sánh ảnh từ 2 URL (Ví dụ: Avatar Cloudinary vs Ảnh Checkin Cloudinary)
     */
    public boolean compareFacesUrls(String sourceImageUrl, String targetImageUrl) {
        if (sourceImageUrl == null || sourceImageUrl.isEmpty() || targetImageUrl == null || targetImageUrl.isEmpty()) {
            log.warn("[Rekognition] Dữ liệu ảnh đầu vào không hợp lệ (URL trống).");
            return false;
        }
        try {
            byte[] targetImageBytes;
            try (InputStream in = new URL(targetImageUrl).openStream()) {
                targetImageBytes = in.readAllBytes();
            }
            return compareFaces(sourceImageUrl, targetImageBytes);
        } catch (Exception e) {
            log.error("[Rekognition] Lỗi tải ảnh checkin từ URL để xử lý: {}", e.getMessage());
            return false;
        }
    }

    /**
     * So sánh ảnh từ URL (Cloudinary) với ảnh chụp (byte array) gửi lên.
     * @param sourceImageUrl URL của ảnh đại diện nhân viên (lưu trên Cloudinary)
     * @param targetImageBytes Mảng byte của ảnh thực tế vừa chụp lúc Check-in
     * @return true nếu khuôn mặt giống nhau >= 90% (theo cấu hình)
     */
    public boolean compareFaces(String sourceImageUrl, byte[] targetImageBytes) {
        if (sourceImageUrl == null || sourceImageUrl.isEmpty() || targetImageBytes == null || targetImageBytes.length == 0) {
            log.warn("[Rekognition] Dữ liệu ảnh đầu vào không hợp lệ (URL trống hoặc file không có data).");
            return false;
        }

        try {
            // 1. Tải ảnh gốc từ Cloudinary về dạng byte array
            byte[] sourceImageBytes;
            try (InputStream in = new URL(sourceImageUrl).openStream()) {
                sourceImageBytes = in.readAllBytes();
            }

            // 2. Chuyển đổi thành SdkBytes của AWS SDK
            Image source = Image.builder()
                    .bytes(SdkBytes.fromByteArray(sourceImageBytes))
                    .build();

            Image target = Image.builder()
                    .bytes(SdkBytes.fromByteArray(targetImageBytes))
                    .build();

            // 3. Cấu hình Request
            CompareFacesRequest request = CompareFacesRequest.builder()
                    .sourceImage(source)
                    .targetImage(target)
                    .similarityThreshold(similarityThreshold)
                    .build();

            // 4. Gọi AWS Rekognition API
            CompareFacesResponse response = rekognitionClient.compareFaces(request);

            // 5. Xử lý kết quả trả về — Log đầy đủ để debug
            log.info("[Rekognition] Threshold đang dùng: {}% | Số khuôn mặt khớp: {}",
                    similarityThreshold, response.faceMatches().size());

            if (!response.faceMatches().isEmpty()) {
                float highestScore = 0;
                for (CompareFacesMatch match : response.faceMatches()) {
                    log.info("[Rekognition] ✅ Khuôn mặt khớp — Similarity: {}%", match.similarity());
                    if (match.similarity() > highestScore) highestScore = match.similarity();
                    if (match.similarity() >= similarityThreshold) {
                        return true;
                    }
                }
                log.warn("[Rekognition] ❌ Điểm cao nhất: {}% — Chưa đạt ngưỡng {}%. Tăng ngưỡng lên hoặc dùng ảnh rõ hơn.",
                        highestScore, similarityThreshold);
            } else {
                // Không có match nào — có thể do ảnh bị biến dạng quá nhiều
                if (!response.unmatchedFaces().isEmpty()) {
                    log.warn("[Rekognition] ❌ Phát hiện {} khuôn mặt trong ảnh checkin nhưng KHÔNG KHỚP với Avatar.",
                            response.unmatchedFaces().size());
                } else {
                    log.warn("[Rekognition] ❌ Không phát hiện được khuôn mặt nào trong ảnh checkin (ảnh mờ, quá tối, hoặc watermark che mặt).");
                }
            }

        } catch (RekognitionException e) {
            log.error("[Rekognition] Lỗi từ phía AWS: {} — ErrorCode: {}", e.getMessage(), e.awsErrorDetails().errorCode());
        } catch (Exception e) {
            log.error("[Rekognition] Lỗi hệ thống khi xử lý ảnh: {}", e.getMessage(), e);
        }

        return false;
    }
}
