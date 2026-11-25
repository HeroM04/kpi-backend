package com.trilong.kpibackend.modules.zalo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/zalo")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Zalo nhóm", description = "Tự đăng Thực chiến / Đào tạo 1-1 đã duyệt lên nhóm Zalo")
public class ZaloNhomController {

    private final ZaloNhomService zalo;

    @Operation(summary = "Bot Zalo đã cài đặt tới đâu")
    @GetMapping("/trang-thai")
    public ResponseEntity<?> trangThai() {
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", zalo.trangThai()));
    }

    @Operation(summary = "Các cuộc trò chuyện vừa nhắn cho bot — để lấy chat_id của nhóm")
    @GetMapping("/nhom-gan-day")
    public ResponseEntity<?> nhomGanDay() {
        try {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", zalo.nhomGanDay()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", String.valueOf(e.getMessage())));
        }
    }

    @Operation(summary = "Gửi thử một tin vào nhóm")
    @PostMapping("/gui-thu")
    public ResponseEntity<?> guiThu(@RequestBody Map<String, String> body) {
        try {
            zalo.guiThu(body.getOrDefault("chatId", "").trim());
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã gửi tin thử."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", String.valueOf(e.getMessage())));
        }
    }
}
