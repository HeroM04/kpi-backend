package com.trilong.kpibackend.modules.kpi.controller;

import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.modules.kpi.dto.KpiScoreResponseDTO;
import com.trilong.kpibackend.modules.kpi.entity.KpiScore;
import com.trilong.kpibackend.modules.kpi.repository.KpiScoreRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/kpi-scores")
@RequiredArgsConstructor
@Tag(name = "KPI Score", description = "Quản lý điểm số KPI tháng của nhân sự")
@Transactional(readOnly = true)
public class KpiController {

    private final KpiScoreRepository kpiScoreRepository;
    private final com.trilong.kpibackend.modules.kpi.repository.KpiWeeklyScoreRepository kpiWeeklyScoreRepository;
    private final UserRepository userRepository;
    private final KpiCalculationService kpiCalculationService;

    @Operation(
            summary = "Xem điểm KPI toàn công ty theo tháng",
            description = "Admin/VP xem điểm KPI. Có thể lọc theo tháng (YYYY-MM) và phòng ban.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping
    @PreAuthorize("hasAuthority('kpi:view-all') or hasRole('ADMIN')")
    public ResponseEntity<?> getAllKpiScores(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Long departmentId) {
        if (month == null || month.trim().isEmpty()) {
            month = kpiCalculationService.extractMonth(ZonedDateTime.now());
        }

        // 1. Lấy tất cả nhân sự đang hoạt động (có thể lọc theo phòng ban)
        List<User> activeUsers = userRepository.findByFilters(departmentId, null, "ACTIVE");

        // 2. Lấy điểm KPI đã có trong DB
        List<KpiScore> scores = kpiScoreRepository.findByMonthAndDepartment(month, departmentId);
        Map<Long, KpiScore> scoreMap = scores.stream().collect(java.util.stream.Collectors.toMap(s -> s.getUser().getId(), s -> s));

        // 2.5 Lấy điểm KPI Tuần
        String currentWeek = kpiCalculationService.getWeekString(ZonedDateTime.now());
        List<com.trilong.kpibackend.modules.kpi.entity.KpiWeeklyScore> weeklyScores = kpiWeeklyScoreRepository.findByWeekAndDepartment(currentWeek, departmentId);
        Map<Long, Integer> weeklyScoreMap = weeklyScores.stream()
                .collect(java.util.stream.Collectors.toMap(w -> w.getUser().getId(), w -> w.getTotal()));

        // 3. Map (nếu ai chưa có điểm thì trả về 0)
        String finalMonth = month;
        int maxMonthlyKpi = kpiCalculationService.getMaxKpiForMonth(finalMonth);
        List<KpiScoreResponseDTO> dtos = activeUsers.stream()
                .map(user -> {
                    KpiScore score = scoreMap.get(user.getId());
                    if (score == null) {
                        score = KpiScore.builder()
                                .user(user)
                                .month(finalMonth)
                                .attendance(0)
                                .meeting(0)
                                .post(0)
                                .deal(0)
                                .total(0)
                                .isFlagged(false)
                                .build();
                    }
                    int weeklyTotal = weeklyScoreMap.getOrDefault(user.getId(), 0);
                    return KpiScoreResponseDTO.from(score, weeklyTotal, maxMonthlyKpi);
                })
                .toList();

        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dtos));
    }

    @Operation(
            summary = "Xem điểm KPI cá nhân tháng hiện tại",
            description = "Nhân sự xem điểm KPI tháng hiện tại hoặc tháng bất kỳ.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyKpiScore(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String month) {
        if (month == null || month.trim().isEmpty()) {
            month = kpiCalculationService.extractMonth(ZonedDateTime.now());
        }

        Long userId = currentUser.getUserId();
        Optional<KpiScore> optScore = kpiScoreRepository.findByUserIdAndMonth(userId, month);

        String currentWeek = kpiCalculationService.getWeekString(ZonedDateTime.now());
        int weeklyTotal = kpiWeeklyScoreRepository.findByUserIdAndWeek(userId, currentWeek)
                .map(com.trilong.kpibackend.modules.kpi.entity.KpiWeeklyScore::getTotal)
                .orElse(0);
        int maxMonthlyKpi = kpiCalculationService.getMaxKpiForMonth(month);

        if (optScore.isPresent()) {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", KpiScoreResponseDTO.from(optScore.get(), weeklyTotal, maxMonthlyKpi)));
        } else {
            // Trả về bản ghi trống nếu chưa có điểm trong tháng này
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
            KpiScore dummy = KpiScore.builder()
                    .user(user)
                    .month(month)
                    .attendance(0)
                    .meeting(0)
                    .post(0)
                    .deal(0)
                    .total(0)
                    .isFlagged(false)
                    .build();
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", KpiScoreResponseDTO.from(dummy, weeklyTotal, maxMonthlyKpi)));
        }
    }

    @Operation(
            summary = "Lịch sử điểm KPI cá nhân nhiều tháng",
            description = "Nhân sự xem lịch sử điểm KPI của mình theo tất cả các tháng — dùng cho màn hình lịch sử trên Mobile.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/my/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyKpiHistory(@AuthenticationPrincipal UserPrincipal currentUser) {
        List<KpiScore> scores = kpiScoreRepository.findByUserIdOrderByMonthDesc(currentUser.getUserId());
        List<KpiScoreResponseDTO> dtos = scores.stream().map(KpiScoreResponseDTO::from).toList();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dtos));
    }

    @Operation(
            summary = "Xem KPI nhân sự trong phòng ban của mình (dành cho Trưởng phòng)",
            description = "Trưởng phòng xem KPI tất cả nhân sự trong phòng mình. " +
                          "departmentId được đọc trực tiếp từ JWT token — không phụ thuộc vào cache phía client.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/my-department")
    @PreAuthorize("hasRole('TRUONG_PHONG') or hasAuthority('kpi:view-all')")
    public ResponseEntity<?> getMyDepartmentKpis(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String month) {

        // Lấy departmentId từ JWT token — đây là nguồn dữ liệu đáng tin cậy duy nhất
        Long departmentId = currentUser.getDepartmentId();
        if (departmentId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "ERROR",
                "message", "Tài khoản này chưa được gán phòng ban."
            ));
        }

        if (month == null || month.trim().isEmpty()) {
            month = kpiCalculationService.extractMonth(ZonedDateTime.now());
        }

        // 1. Lấy tất cả nhân sự ACTIVE trong phòng ban của mình (theo JWT)
        List<User> activeUsers = userRepository.findByFilters(departmentId, null, "ACTIVE");

        // 2. Lấy điểm KPI đã có trong DB cho phòng ban đó
        List<KpiScore> scores = kpiScoreRepository.findByMonthAndDepartment(month, departmentId);
        Map<Long, KpiScore> scoreMap = scores.stream()
                .collect(java.util.stream.Collectors.toMap(s -> s.getUser().getId(), s -> s));

        // 2.5 Lấy điểm KPI Tuần
        String currentWeek = kpiCalculationService.getWeekString(ZonedDateTime.now());
        List<com.trilong.kpibackend.modules.kpi.entity.KpiWeeklyScore> weeklyScores = kpiWeeklyScoreRepository.findByWeekAndDepartment(currentWeek, departmentId);
        Map<Long, Integer> weeklyScoreMap = weeklyScores.stream()
                .collect(java.util.stream.Collectors.toMap(w -> w.getUser().getId(), w -> w.getTotal()));

        // 3. Map — ai chưa có điểm thì trả về 0
        String finalMonth = month;
        int maxMonthlyKpi = kpiCalculationService.getMaxKpiForMonth(finalMonth);
        List<KpiScoreResponseDTO> dtos = activeUsers.stream()
                .map(user -> {
                    KpiScore score = scoreMap.get(user.getId());
                    if (score == null) {
                        score = KpiScore.builder()
                                .user(user)
                                .month(finalMonth)
                                .attendance(0)
                                .meeting(0)
                                .post(0)
                                .deal(0)
                                .total(0)
                                .isFlagged(false)
                                .build();
                    }
                    int weeklyTotal = weeklyScoreMap.getOrDefault(user.getId(), 0);
                    return KpiScoreResponseDTO.from(score, weeklyTotal, maxMonthlyKpi);
                })
                .toList();

        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dtos));
    }


    @Operation(
            summary = "Cắm cờ / Đánh dấu nghi ngờ bản ghi KPI",
            description = "Dành cho Admin hoặc HR nhằm cắm cờ các tháng KPI có nghi ngờ gian lận để hậu kiểm.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PutMapping("/{id}/flag")
    @PreAuthorize("hasAuthority('kpi:flag') or hasRole('ADMIN')")
    public ResponseEntity<?> flagKpiScore(
            @PathVariable Long id,
            @RequestParam boolean isFlagged) {
        KpiScore kpiScore = kpiScoreRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bản ghi KPI có ID: " + id));
        kpiScore.setFlagged(isFlagged);
        kpiScoreRepository.save(kpiScore);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", isFlagged ? "Đã cắm cờ bản ghi KPI này." : "Đã gỡ cờ bản ghi KPI này.",
                "data", KpiScoreResponseDTO.from(kpiScore)
        ));
    }
}

