package com.trilong.kpibackend.modules.deal.controller;

import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.modules.deal.dto.DealResponseDTO;
import com.trilong.kpibackend.modules.deal.dto.SubmitDealDTO;
import com.trilong.kpibackend.modules.deal.entity.Deal;
import com.trilong.kpibackend.modules.deal.service.DealService;
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
@RequestMapping("/api/v1/deals")
@RequiredArgsConstructor
@Tag(name = "Deals", description = "Quản lý chốt căn (giao dịch bất động sản của Sales)")
@SecurityRequirement(name = "Bearer Authentication")
public class DealController {

    private final DealService dealService;

    @Operation(summary = "Sales gửi yêu cầu chốt căn", description = "Lưu yêu cầu chốt căn dưới dạng PENDING.")
    @PostMapping("/submit")
    @PreAuthorize("hasAuthority('deal:submit')")
    public ResponseEntity<?> submitDeal(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody SubmitDealDTO dto) {
        Deal deal = dealService.submitDeal(currentUser.getUserId(), dto);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đã gửi yêu cầu chốt căn thành công!",
                "data", DealResponseDTO.from(deal)
        ));
    }

    @Operation(summary = "Xem lịch sử chốt căn của chính mình", description = "Sales xem các giao dịch chốt căn của bản thân.")
    @GetMapping("/my-deals")
    @PreAuthorize("hasAuthority('deal:view-my')")
    public ResponseEntity<?> getMyDeals(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String date) {
        LocalDate filterDate = (date != null && !date.trim().isEmpty()) ? LocalDate.parse(date) : LocalDate.now();
        List<Deal> deals = dealService.getMyDeals(currentUser.getUserId()).stream()
                .filter(d -> d.getSubmittedAt() != null && d.getSubmittedAt().toLocalDate().equals(filterDate))
                .toList();
        List<DealResponseDTO> dtos = deals.stream().map(DealResponseDTO::from).toList();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dtos));
    }

    @Operation(summary = "Xem danh sách chốt căn chờ duyệt", description = "Dành cho Admin/Trưởng phòng xem các deal PENDING.")
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('deal:approve') or hasRole('ADMIN')")
    public ResponseEntity<?> getPendingDeals() {
        List<Deal> deals = dealService.getDealsByStatus("PENDING");
        List<DealResponseDTO> dtos = deals.stream().map(DealResponseDTO::from).toList();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dtos));
    }

    @Operation(summary = "Xem toàn bộ danh sách chốt căn", description = "Dành cho Admin quản lý tất cả các deal.")
    @GetMapping
    @PreAuthorize("hasAuthority('deal:manage') or hasRole('ADMIN')")
    public ResponseEntity<?> getAllDeals() {
        List<Deal> deals = dealService.getAllDeals();
        List<DealResponseDTO> dtos = deals.stream().map(DealResponseDTO::from).toList();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dtos));
    }

    @Operation(summary = "Duyệt chốt căn", description = "Phê duyệt giao dịch, tự động cộng điểm KPI.")
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('deal:approve') or hasRole('ADMIN')")
    public ResponseEntity<?> approveDeal(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Deal deal = dealService.approveDeal(id, currentUser.getUserId());
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Phê duyệt giao dịch chốt căn thành công!",
                "data", DealResponseDTO.from(deal)
        ));
    }

    @Operation(summary = "Từ chối chốt căn", description = "Từ chối giao dịch, tự động trừ điểm nếu trước đó đã duyệt.")
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('deal:approve') or hasRole('ADMIN')")
    public ResponseEntity<?> rejectDeal(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Deal deal = dealService.rejectDeal(id, currentUser.getUserId());
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đã từ chối giao dịch chốt căn.",
                "data", DealResponseDTO.from(deal)
        ));
    }

    @Operation(summary = "Xem chi tiết yêu cầu chốt căn")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getDealById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            Deal deal = dealService.getDealById(id);
            boolean isAdminOrManager = currentUser.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("deal:manage") || a.getAuthority().equals("deal:approve") || a.getAuthority().equals("ROLE_ADMIN"));

            if (!isAdminOrManager && !deal.getUser().getId().equals(currentUser.getUserId())) {
                return ResponseEntity.status(403).body(Map.of("status", "ERROR", "message", "Bạn không có quyền xem giao dịch này."));
            }

            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", DealResponseDTO.from(deal)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Xóa yêu cầu chốt căn", description = "Admin xóa yêu cầu chốt căn, tự động trừ điểm nếu deal đang ở APPROVED.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteDeal(@PathVariable Long id) {
        dealService.deleteDeal(id);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đã xóa yêu cầu chốt căn thành công!"
        ));
    }
}

