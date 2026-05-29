package com.trilong.kpibackend.modules.feedback.controller;

import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.modules.feedback.dto.FeedbackResponseDTO;
import com.trilong.kpibackend.modules.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/feedbacks")
@RequiredArgsConstructor
@Tag(name = "Feedbacks", description = "Quản lý ý kiến, khiếu nại và phản hồi của nhân viên")
@SecurityRequirement(name = "Bearer Authentication")
public class FeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "Nhân viên gửi feedback", description = "Lưu phản hồi dưới dạng UNREAD và broadcast tới Admin/HR.")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> submitFeedback(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody Map<String, Object> request) {
        try {
            FeedbackResponseDTO response = feedbackService.createAndBroadcastFeedback(currentUser.getUserId(), request);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Đã gửi feedback thành công!",
                    "data", response
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Xem toàn bộ feedback", description = "Dành cho Admin/HR để quản lý các khiếu nại.")
    @GetMapping
    @PreAuthorize("hasAuthority('feedback:manage') or hasRole('ADMIN')")
    public ResponseEntity<?> getAllFeedbacks() {
        List<FeedbackResponseDTO> list = feedbackService.getAllFeedbacks();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", list));
    }

    @Operation(summary = "Phản hồi feedback của nhân viên", description = "Dành cho Admin/HR trả lời khiếu nại.")
    @PutMapping("/{id}/reply")
    @PreAuthorize("hasAuthority('feedback:manage') or hasRole('ADMIN')")
    public ResponseEntity<?> replyFeedback(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody Map<String, String> requestBody) {
        String replyText = requestBody.get("reply");
        if (replyText == null) {
            replyText = requestBody.get("replyText");
        }
        if (replyText == null || replyText.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "Nội dung phản hồi không được để trống."));
        }

        try {
            FeedbackResponseDTO response = feedbackService.replyFeedback(id, currentUser.getUserId(), replyText);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Đã gửi phản hồi thành công!",
                    "data", response
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Xem danh sách feedback cá nhân đã gửi")
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyFeedbacks(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String date) {
        try {
            LocalDate filterDate = (date != null && !date.trim().isEmpty()) ? LocalDate.parse(date) : LocalDate.now();
            List<FeedbackResponseDTO> list = feedbackService.getFeedbacksBySender(currentUser.getUserId()).stream()
                    .filter(f -> f.getCreatedAt() != null && f.getCreatedAt().toLocalDate().equals(filterDate))
                    .toList();
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", list));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Xem chi tiết góp ý")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getFeedbackById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            FeedbackResponseDTO dto = feedbackService.getFeedbackById(id);
            boolean isAdminOrStaff = currentUser.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("feedback:manage") || a.getAuthority().equals("ROLE_ADMIN"));
            
            if (!isAdminOrStaff && !currentUser.getUserId().equals(dto.getSenderId())) {
                return ResponseEntity.status(403).body(Map.of("status", "ERROR", "message", "Bạn không có quyền xem góp ý này."));
            }
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dto));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Cập nhật trạng thái góp ý", description = "Chỉ dành cho Admin/HR để đổi trạng thái READ/RESOLVED.")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('feedback:manage') or hasRole('ADMIN')")
    public ResponseEntity<?> updateFeedbackStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        try {
            FeedbackResponseDTO dto = feedbackService.updateStatus(id, status);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã cập nhật trạng thái thành công", "data", dto));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Xóa góp ý", description = "Chỉ dành cho Admin/HR.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('feedback:manage') or hasRole('ADMIN')")
    public ResponseEntity<?> deleteFeedback(@PathVariable Long id) {
        try {
            feedbackService.deleteFeedback(id);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã xóa góp ý thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
}