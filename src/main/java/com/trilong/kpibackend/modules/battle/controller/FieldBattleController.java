package com.trilong.kpibackend.modules.battle.controller;

import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.modules.battle.dto.FieldBattleResponseDTO;
import com.trilong.kpibackend.modules.battle.dto.SubmitFieldBattleDTO;
import com.trilong.kpibackend.modules.battle.entity.FieldBattle;
import com.trilong.kpibackend.modules.battle.service.FieldBattleService;
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
@RequestMapping("/api/v1/field-battle")
@RequiredArgsConstructor
@Tag(name = "Field Battles", description = "Quản lý báo cáo thực chiến gặp khách của Sales")
@SecurityRequirement(name = "Bearer Authentication")
public class FieldBattleController {

    private final FieldBattleService fieldBattleService;

    @Operation(summary = "Sales gửi báo cáo thực chiến", description = "Lưu báo cáo thực chiến dưới dạng PENDING.")
    @PostMapping("/submit")
    @PreAuthorize("hasAuthority('meeting:submit')")
    public ResponseEntity<?> submitBattle(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody SubmitFieldBattleDTO dto) {
        FieldBattle battle = fieldBattleService.submitBattle(currentUser.getUserId(), dto);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đã gửi báo cáo thực chiến thành công!",
                "data", FieldBattleResponseDTO.from(battle)
        ));
    }

    @Operation(summary = "Xem lịch sử thực chiến của chính mình", description = "Lấy tất cả các thực chiến của Sales đang đăng nhập.")
    @GetMapping("/my-battles")
    @PreAuthorize("hasAuthority('meeting:view-my')")
    public ResponseEntity<?> getMyBattles(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String date) {
        LocalDate filterDate = (date != null && !date.trim().isEmpty()) ? LocalDate.parse(date) : LocalDate.now();
        List<FieldBattle> battles = fieldBattleService.getMyBattles(currentUser.getUserId()).stream()
                .filter(b -> b.getSubmittedAt() != null && b.getSubmittedAt().toLocalDate().equals(filterDate))
                .toList();
        List<FieldBattleResponseDTO> dtos = battles.stream().map(FieldBattleResponseDTO::from).toList();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dtos));
    }

    @Operation(summary = "Xem danh sách thực chiến chờ duyệt", description = "Dành cho Admin/Trưởng phòng để duyệt.")
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('meeting:approve') or hasRole('ADMIN')")
    public ResponseEntity<?> getPendingBattles() {
        List<FieldBattle> battles = fieldBattleService.getBattlesByStatus("PENDING");
        List<FieldBattleResponseDTO> dtos = battles.stream().map(FieldBattleResponseDTO::from).toList();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dtos));
    }

    @Operation(summary = "Xem toàn bộ báo cáo thực chiến", description = "Dành cho Admin quản lý tất cả.")
    @GetMapping
    @PreAuthorize("hasAuthority('meeting:manage') or hasRole('ADMIN')")
    public ResponseEntity<?> getAllBattles() {
        List<FieldBattle> battles = fieldBattleService.getAllBattles();
        List<FieldBattleResponseDTO> dtos = battles.stream().map(FieldBattleResponseDTO::from).toList();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dtos));
    }

    @Operation(summary = "Duyệt báo cáo thực chiến", description = "Phê duyệt báo cáo, tự động cộng điểm KPI.")
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('meeting:approve') or hasRole('ADMIN')")
    public ResponseEntity<?> approveBattle(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        FieldBattle battle = fieldBattleService.approveBattle(id, currentUser.getUserId());
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Duyệt báo cáo thực chiến thành công!",
                "data", FieldBattleResponseDTO.from(battle)
        ));
    }

    @Operation(summary = "Từ chối báo cáo thực chiến", description = "Từ chối báo cáo, tự động trừ điểm nếu đã duyệt trước đó.")
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('meeting:approve') or hasRole('ADMIN')")
    public ResponseEntity<?> rejectBattle(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        FieldBattle battle = fieldBattleService.rejectBattle(id, currentUser.getUserId());
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đã từ chối báo cáo thực chiến.",
                "data", FieldBattleResponseDTO.from(battle)
        ));
    }

    @Operation(summary = "Xem chi tiết báo cáo thực chiến")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getBattleById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            FieldBattle battle = fieldBattleService.getBattleById(id);
            boolean isAdminOrManager = currentUser.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("meeting:manage") || a.getAuthority().equals("meeting:approve") || a.getAuthority().equals("ROLE_ADMIN"));

            if (!isAdminOrManager && !battle.getUser().getId().equals(currentUser.getUserId())) {
                return ResponseEntity.status(403).body(Map.of("status", "ERROR", "message", "Bạn không có quyền xem báo cáo này."));
            }

            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", FieldBattleResponseDTO.from(battle)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Xóa báo cáo thực chiến", description = "Admin xóa báo cáo thực chiến, tự động trừ điểm nếu đã duyệt.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteBattle(@PathVariable Long id) {
        fieldBattleService.deleteBattle(id);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đã xóa báo cáo thực chiến thành công!"
        ));
    }
}

