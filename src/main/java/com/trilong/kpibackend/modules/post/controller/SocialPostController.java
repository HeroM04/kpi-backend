package com.trilong.kpibackend.modules.post.controller;

import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.modules.post.dto.SocialPostResponseDTO;
import com.trilong.kpibackend.modules.post.dto.SubmitPostDTO;
import com.trilong.kpibackend.modules.post.entity.SocialPost;
import com.trilong.kpibackend.modules.post.service.SocialPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/social-posts")
@RequiredArgsConstructor
@Tag(name = "Social Posts", description = "Quản lý bài đăng truyền thông MXH của Sales")
@SecurityRequirement(name = "Bearer Authentication")
public class SocialPostController {

    private final SocialPostService socialPostService;

    @Operation(summary = "Sales gửi bài viết MXH", description = "Lưu bài đăng MXH dưới dạng PENDING để chờ duyệt.")
    @PostMapping("/submit")
    @PreAuthorize("hasAuthority('post:submit')")
    public ResponseEntity<?> submitPost(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody SubmitPostDTO dto) {
        SocialPost post = socialPostService.submitPost(currentUser.getUserId(), dto);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đã gửi bài đăng MXH thành công!",
                "data", SocialPostResponseDTO.from(post)
        ));
    }

    @Operation(summary = "Xem lịch sử bài viết MXH của bản thân", description = "Lấy tất cả bài đăng MXH của Sales đang đăng nhập.")
    @GetMapping("/my-posts")
    @PreAuthorize("hasAuthority('post:view-my')")
    public ResponseEntity<?> getMyPosts(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String date) {
        LocalDate filterDate = (date != null && !date.trim().isEmpty()) ? LocalDate.parse(date) : LocalDate.now();
        List<SocialPost> posts = socialPostService.getMyPosts(currentUser.getUserId()).stream()
                .filter(p -> p.getSubmittedAt() != null && p.getSubmittedAt().toLocalDate().equals(filterDate))
                .toList();
        List<SocialPostResponseDTO> dtos = posts.stream().map(SocialPostResponseDTO::from).toList();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dtos));
    }

    @Operation(summary = "Xem bài viết MXH chờ duyệt", description = "Dành cho Admin/Trưởng phòng để kiểm tra danh sách chờ.")
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('post:approve') or hasRole('ADMIN')")
    public ResponseEntity<?> getPendingPosts() {
        List<SocialPost> posts = socialPostService.getPostsByStatus("PENDING");
        List<SocialPostResponseDTO> dtos = posts.stream().map(SocialPostResponseDTO::from).toList();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dtos));
    }

    @Operation(summary = "Xem toàn bộ bài đăng MXH trong công ty", description = "Dành cho Admin quản lý tất cả.")
    @GetMapping
    @PreAuthorize("hasAuthority('post:manage') or hasRole('ADMIN')")
    public ResponseEntity<?> getAllPosts() {
        List<SocialPost> posts = socialPostService.getAllPosts();
        List<SocialPostResponseDTO> dtos = posts.stream().map(SocialPostResponseDTO::from).toList();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dtos));
    }

    @Operation(summary = "Duyệt bài đăng MXH", description = "Duyệt bài và cộng điểm KPI cho nhân viên.")
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('post:approve') or hasRole('ADMIN')")
    public ResponseEntity<?> approvePost(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        SocialPost post = socialPostService.approvePost(id, currentUser.getUserId());
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Duyệt bài đăng MXH thành công!",
                "data", SocialPostResponseDTO.from(post)
        ));
    }

    @Operation(summary = "Từ chối bài đăng MXH", description = "Từ chối duyệt bài, thu hồi điểm nếu đã duyệt trước đó.")
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('post:approve') or hasRole('ADMIN')")
    public ResponseEntity<?> rejectPost(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        SocialPost post = socialPostService.rejectPost(id, currentUser.getUserId());
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đã từ chối bài đăng MXH.",
                "data", SocialPostResponseDTO.from(post)
        ));
    }

    @Operation(summary = "Xem chi tiết bài đăng MXH")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getPostById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            SocialPost post = socialPostService.getPostById(id);
            boolean isAdminOrManager = currentUser.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("post:manage") || a.getAuthority().equals("post:approve") || a.getAuthority().equals("ROLE_ADMIN"));

            if (!isAdminOrManager && !post.getUser().getId().equals(currentUser.getUserId())) {
                return ResponseEntity.status(403).body(Map.of("status", "ERROR", "message", "Bạn không có quyền xem bài đăng này."));
            }

            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", SocialPostResponseDTO.from(post)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Xóa bài đăng MXH", description = "Admin xóa bản ghi bài đăng MXH, tự động thu hồi điểm nếu đã duyệt.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        socialPostService.deletePost(id);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đã xóa bài đăng MXH thành công!"
        ));
    }
}

