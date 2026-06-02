package com.trilong.kpibackend.modules.training.controller;

import com.trilong.kpibackend.modules.training.dto.OneOnOneTrainingDto;
import com.trilong.kpibackend.modules.training.service.OneOnOneTrainingService;
import com.trilong.kpibackend.core.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/training/1-on-1")
@RequiredArgsConstructor
@Tag(name = "One-On-One Training API", description = "Quản lý đào tạo 1-1")
public class OneOnOneTrainingController {

    private final OneOnOneTrainingService service;

    @Operation(summary = "Lấy danh sách Đào tạo 1-1", description = "Dành cho Admin")
    @GetMapping
    public ResponseEntity<List<OneOnOneTrainingDto>> getAll() {
        return ResponseEntity.ok(service.getAllOneOnOneTrainings());
    }

    @Operation(summary = "Nộp báo cáo đào tạo 1-1", description = "Dành cho nhân sự sử dụng App")
    @PostMapping
    public ResponseEntity<?> submitOneOnOne(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody Map<String, String> payload
    ) {
        String content = payload.get("content");
        String photoUrl = payload.get("photoUrl");

        if (content == null || content.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Nội dung không được để trống"));
        }

        OneOnOneTrainingDto result = service.submitOneOnOneTraining(user.getUserId(), content, photoUrl);
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Nộp Đào tạo 1-1 thành công. Đã cộng 5 điểm KPI.",
                "data", result
        ));
    }
}
