package com.trilong.kpibackend.modules.training.controller;

import com.trilong.kpibackend.modules.training.dto.OneOnOneTrainingDto;
import com.trilong.kpibackend.modules.training.service.OneOnOneTrainingService;
import com.trilong.kpibackend.core.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    /*
     * Trước đây không gắn quyền: nhân sự nào đăng nhập app cũng đọc được báo cáo
     * 1-1 của cả công ty qua API này. App không dùng tới nó — chỉ web quản trị.
     */
    @Operation(summary = "Lấy danh sách Đào tạo 1-1", description = "Dành cho web quản trị")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VAN_PHONG')")
    public ResponseEntity<List<OneOnOneTrainingDto>> getAll() {
        return ResponseEntity.ok(service.getAllOneOnOneTrainings());
    }

    @Operation(summary = "Nộp báo cáo đào tạo 1-1",
               description = "Dành cho nhân sự dùng app. Báo cáo vào trạng thái chờ duyệt, "
                           + "Admin duyệt thì mới cộng điểm.")
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
                "message", "Đã gửi báo cáo đào tạo 1-1. Admin duyệt xong sẽ cộng điểm.",
                "data", result
        ));
    }

    @Operation(summary = "Chuyển các báo cáo tự duyệt đời cũ về chờ duyệt",
               description = "Báo cáo được máy chủ tự duyệt trước khi có bước duyệt tay: đưa về chờ duyệt "
                           + "và hoàn lại đúng số điểm đã thực cộng. Chạy lại lần hai không làm gì thêm.")
    @PostMapping("/reset-auto-approved")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> chuyenVeChoDuyet() {
        try {
            var kq = service.chuyenBaoCaoTuDuyetVeChoDuyet();
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", kq,
                    "message", "Đã chuyển " + kq.soBaoCao() + " báo cáo của " + kq.soNguoi()
                            + " nhân sự về chờ duyệt, hoàn lại " + kq.tongDiemHoan() + "đ."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Duyệt báo cáo đào tạo 1-1", description = "Cộng 5đ nhóm Thực chiến vào tuần nộp báo cáo.")
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> duyet(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal admin) {
        try {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", service.duyet(id, admin.getUserId())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Từ chối báo cáo đào tạo 1-1",
               description = "Báo cáo đã được duyệt trước đó thì thu hồi 5đ đã cộng.")
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> tuChoi(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal admin) {
        try {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", service.tuChoi(id, admin.getUserId())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
}
