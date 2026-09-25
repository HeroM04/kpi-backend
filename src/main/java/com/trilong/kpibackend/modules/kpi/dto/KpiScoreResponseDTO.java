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
        return from(score, weeklyTotal, maxMonthlyKpi, false);
    }

    /**
     * @param coChotCan tháng này nhân sự có chốt căn đã duyệt — tra từ bảng chốt
     *                  căn (cùng cách báo cáo Excel xếp loại). Duyệt chốt căn cố ý
     *                  KHÔNG cộng điểm nên cột {@code deal} của bảng điểm luôn 0;
     *                  trước đây chỉ nhìn cột đó nên web/app không bao giờ hiện
     *                  "Hoàn thành 100% (Chốt căn)", trong khi Excel thì có.
     */
    public static KpiScoreResponseDTO from(KpiScore score, int weeklyTotal, int maxMonthlyKpi, boolean coChotCan) {
        if (score == null) return null;

        // LUẬT KPI: Nếu chốt căn, tự động đạt tối đa KPI Tháng và KPI Tuần
        boolean chotCan = coChotCan || score.getDeal() > 0;
        int displayTotal = score.getTotal();
        int displayWeeklyTotal = weeklyTotal;
        if (chotCan) {
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
                // Web và app nhận biết chốt căn qua deal > 0
                .deal(chotCan ? Math.max(1, score.getDeal()) : score.getDeal())
                .total(displayTotal)
                .weeklyTotal(displayWeeklyTotal)
                .isFlagged(score.isFlagged())
                .build();
    }

    private static int calculateMaxKpiForMonth(String monthStr) {
        try {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM");
            java.time.YearMonth ym = java.time.YearMonth.parse(monthStr, formatter);
            int mondays = 0;
            for (int i = 1; i <= ym.lengthOfMonth(); i++) {
                if (ym.atDay(i).getDayOfWeek() == java.time.DayOfWeek.MONDAY) {
                    mondays++;
                }
            }
            return mondays * 100;
        } catch (Exception e) {
            return 400;
        }
    }

    public static KpiScoreResponseDTO from(KpiScore score, int weeklyTotal) {
        if (score == null) return null;
        int maxMonthlyKpi = calculateMaxKpiForMonth(score.getMonth());
        return from(score, weeklyTotal, maxMonthlyKpi);
    }
    
    // Fallback for missing weeklyTotal
    public static KpiScoreResponseDTO from(KpiScore score) {
        if (score == null) return null;
        int maxMonthlyKpi = calculateMaxKpiForMonth(score.getMonth());
        return from(score, 0, maxMonthlyKpi);
    }
}
