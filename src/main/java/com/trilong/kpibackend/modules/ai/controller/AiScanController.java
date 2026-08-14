package com.trilong.kpibackend.modules.ai.controller;

import com.trilong.kpibackend.modules.ai.dto.ScanPostRequestDTO;
import com.trilong.kpibackend.modules.ai.service.GeminiScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AiScanController — API chấm điểm bài đăng bằng AI.
 *
 * <p>Web gọi endpoint này thay vì gọi thẳng Google, nhờ đó API key của Gemini
 * chỉ nằm trên server (biến môi trường {@code GEMINI_API_KEY}) và không bao giờ
 * lộ ra trình duyệt.
 *
 * <p>Chỉ người đã đăng nhập mới gọi được — tránh bị lạm dụng gọi AI miễn phí.
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Scanner", description = "Chấm điểm bài đăng truyền thông BĐS bằng Gemini")
@SecurityRequirement(name = "Bearer Authentication")
public class AiScanController {

    private final GeminiScanService geminiScanService;

    @Operation(
            summary = "Chấm điểm bài đăng bằng AI",
            description = "Phân tích caption + ảnh chụp màn hình, trả về điểm 0-100 và đề xuất RECOMMEND/REVIEW."
    )
    @PostMapping("/scan-post")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> scanPost(@RequestBody ScanPostRequestDTO request) {
        Map<String, Object> result = geminiScanService.scanPost(
                request.getCaption(), request.getScreenshotUrl());
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", result));
    }
}
