package com.trilong.kpibackend.modules.kpi.dto;

import com.trilong.kpibackend.modules.kpi.entity.KpiScore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiScoreResponseDTO {
    private Long id;
    private Long userId;
    private String fullName;
    private String role;
    private String departmentName;
    private String month;
    private int attendance;
    private int meeting;
    private int post;
    private int deal;
    private int total;
    private int weeklyTotal;
    private boolean isFlagged;

    public static KpiScoreResponseDTO from(KpiScore score, int weeklyTotal, int maxMonthlyKpi) {
        if (score == null) return null;
        
        // LUẬT KPI: Nếu chốt căn (deal > 0), tự động đạt tối đa KPI Tháng và KPI Tuần
        int displayTotal = score.getTotal();
        int displayWeeklyTotal = weeklyTotal;
        if (score.getDeal() > 0) {
            displayTotal = maxMonthlyKpi;
            displayWeeklyTotal = 100; // KPI Tuần chuẩn là 100
        }
        
        return KpiScoreResponseDTO.builder()
                .id(score.getId())
                .userId(score.getUser().getId())
                .fullName(score.getUser().getFullName())
                .role(score.getUser().getRole())
                .departmentName(score.getUser().getDepartment() != null ? score.getUser().getDepartment().getName() : null)
                .month(score.getMonth())
                .attendance(score.getAttendance())
                .meeting(score.getMeeting())
                .post(score.getPost())
                .deal(score.getDeal())
                .total(displayTotal)
                .weeklyTotal(displayWeeklyTotal)
                .isFlagged(score.isFlagged())
                .build();
    }

    public static KpiScoreResponseDTO from(KpiScore score, int weeklyTotal) {
        return from(score, weeklyTotal, 400); // 400 là mặc định nếu không truyền
    }
    
    // Fallback for missing weeklyTotal
    public static KpiScoreResponseDTO from(KpiScore score) {
        return from(score, 0, 400);
    }
}
