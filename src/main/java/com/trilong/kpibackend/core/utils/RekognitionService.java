package com.trilong.kpibackend.core.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.CompareFacesRequest;
import software.amazon.awssdk.services.rekognition.model.CompareFacesResponse;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.S3Object;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * RekognitionService — thực hiện so khớp khuôn mặt bằng AWS Rekognition.
 */
@Service
@Slf4j
public class RekognitionService {

    @Autowired(required = false)
    private RekognitionClient rekognitionClient;

    @Value("${app.storage.local.base-url:http://localhost:8080/uploads}")
    private String localBaseUrl;

    @Value("${app.storage.local.dir:uploads}")
    private String localUploadDir;

    @Value("${aws.s3.base-url:}")
    private String s3BaseUrl;

    @Value("${aws.s3.bucket-name:}")
    private String s3BucketName;

    @Value("${app.rekognition.enabled:false}")
    private boolean rekognitionEnabled;

    @Value("${aws.rekognition.similarity-threshold:80.0}")
    private float similarityThreshold;

    /**
     * So sánh khuôn mặt trong hai ảnh.
     * Trả về true nếu độ tương đồng >= similarityThreshold.
     */
    public boolean compareFaces(String sourceUrl, String targetUrl) {
        if (!rekognitionEnabled || rekognitionClient == null) {
            log.info("AWS Rekognition bị tắt hoặc chưa được cấu hình. Tự động bỏ qua xác thực khuôn mặt.");
            return true;
        }

        if (sourceUrl == null || sourceUrl.isBlank()) {
            log.warn("Source URL rỗng, không thể thực hiện so khớp khuôn mặt.");
            return false;
        }
        if (targetUrl == null || targetUrl.isBlank()) {
            log.warn("Target URL rỗng, không thể thực hiện so khớp khuôn mặt.");
            return false;
        }

        try {
            log.info("Bắt đầu so khớp khuôn mặt: sourceUrl={}, targetUrl={}", sourceUrl, targetUrl);

            Image sourceImage = getImageFromUrl(sourceUrl);
            Image targetImage = getImageFromUrl(targetUrl);

            CompareFacesRequest request = CompareFacesRequest.builder()
                    .sourceImage(sourceImage)
                    .targetImage(targetImage)
                    .similarityThreshold(similarityThreshold)
                    .build();

            CompareFacesResponse response = rekognitionClient.compareFaces(request);

            if (!response.faceMatches().isEmpty()) {
                float similarity = response.faceMatches().get(0).similarity();
                log.info("Nhận diện khuôn mặt thành công! Độ tương đồng: {}% (Ngưỡng yêu cầu: {}%)", 
                        similarity, similarityThreshold);
                return true;
            } else {
                log.warn("Nhận diện khuôn mặt thất bại: Không tìm thấy khuôn mặt trùng khớp với độ tương đồng >= {}%", 
                        similarityThreshold);
                return false;
            }

        } catch (Exception e) {
            log.error("Lỗi hệ thống khi gọi AWS Rekognition CompareFaces: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi hệ thống xác thực khuôn mặt: " + e.getMessage() + 
                    ". Nếu muốn tắt tính năng này để dev offline, hãy set 'app.rekognition.enabled=false' trong application.properties.", e);
        }
    }

    /**
     * Tạo đối tượng Image từ URL. Tự động tối ưu hóa nếu là S3 URL hoặc local URL.
     */
    private Image getImageFromUrl(String url) throws Exception {
        // 1. Trường hợp là S3 URL của chúng ta
        if (s3BaseUrl != null && !s3BaseUrl.isBlank() && url.startsWith(s3BaseUrl + "/")) {
            String key = url.replace(s3BaseUrl + "/", "");
            log.info("Tối ưu hóa: Đọc ảnh trực tiếp từ S3. Bucket: {}, Key: {}", s3BucketName, key);
            return Image.builder()
                    .s3Object(S3Object.builder()
                            .bucket(s3BucketName)
                            .name(key)
                            .build())
                    .build();
        }

        // 2. Trường hợp là local URL (khi lưu ảnh local)
        if (url.startsWith(localBaseUrl + "/")) {
            String relativePath = url.replace(localBaseUrl + "/", "");
            log.info("Tối ưu hóa: Đọc ảnh trực tiếp từ local filesystem. Path: {}/{}", localUploadDir, relativePath);
            byte[] bytes = Files.readAllBytes(Paths.get(localUploadDir, relativePath));
            return Image.builder()
                    .bytes(SdkBytes.fromByteArray(bytes))
                    .build();
        }

        // 3. Dự phòng: Tải ảnh từ internet về bytes
        log.info("Tải ảnh từ external URL về bytes: {}", url);
        byte[] bytes;
        try (InputStream in = URI.create(url).toURL().openStream()) {
            bytes = in.readAllBytes();
        }
        return Image.builder()
                .bytes(SdkBytes.fromByteArray(bytes))
                .build();
    }
}
