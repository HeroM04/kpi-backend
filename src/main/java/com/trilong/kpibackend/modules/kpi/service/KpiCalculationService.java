package com.trilong.kpibackend.modules.kpi.service;

import com.trilong.kpibackend.modules.kpi.entity.KpiScore;
import com.trilong.kpibackend.modules.kpi.entity.KpiWeeklyScore;
import com.trilong.kpibackend.modules.kpi.repository.KpiScoreRepository;
import com.trilong.kpibackend.modules.kpi.repository.KpiWeeklyScoreRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.trilong.kpibackend.modules.kpi.dto.KpiScoreResponseDTO;
import jakarta.annotation.PostConstruct;
import java.util.Map;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KpiCalculationService {

    private final KpiScoreRepository kpiScoreRepository;
    private final KpiWeeklyScoreRepository kpiWeeklyScoreRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @PostConstruct
    public void initMissingKpiScores() {
        String currentMonth = extractMonth(ZonedDateTime.now());
        List<User> activeUsers = userRepository.findAll();
        
        for (User user : activeUsers) {
            if ("ACTIVE".equals(user.getStatus()) && ("SALE".equals(user.getRole()) || "TRUONG_PHONG".equals(user.getRole()))) {
                kpiScoreRepository.findByUserIdAndMonth(user.getId(), currentMonth)
                        .orElseGet(() -> {
                            KpiScore dummy = KpiScore.builder()
                                    .user(user)
                                    .month(currentMonth)
                                    .attendance(0)
                                    .meeting(0)
                                    .post(0)
                                    .deal(0)
                                    .total(0)
                                    .isFlagged(false)
                                    .build();
                            return kpiScoreRepository.save(dummy);
                        });
            }
        }
    }

    /**
     * Xác định "tháng KPI" của một mốc thời gian.
     *
     * <p>Quy tắc công ty: <b>một tuần thuộc trọn về một tháng</b> — tháng chứa
     * ngày THỨ HAI của tuần đó. Nhờ vậy tuần cuối tháng không bị cắt đôi:
     * ví dụ tuần 31/08–06/09/2026 tính trọn cho tháng 8, nên việc làm ngày
     * 01–06/09 vẫn cộng vào KPI tháng 8; tháng 9 bắt đầu từ thứ Hai 07/09.
     *
     * <p>Cách này cũng khớp với {@link #getMaxKpiForMonth(String)} (đếm số ngày
     * thứ Hai trong tháng × 100 → tháng 4 tuần = 400 điểm, 5 tuần = 500 điểm).
     */
    private String monthOfWeek(java.time.LocalDate date) {
        java.time.LocalDate monday = date.with(java.time.temporal.WeekFields.ISO.dayOfWeek(), 1);
        return String.format("%d-%02d", monday.getYear(), monday.getMonthValue());
    }

    public String extractMonth(ZonedDateTime dateTime) {
        ZonedDateTime t = (dateTime == null) ? ZonedDateTime.now(VN_ZONE)
                : dateTime.withZoneSameInstant(VN_ZONE);
        return monthOfWeek(t.toLocalDate());
    }

    public String extractMonth(LocalDateTime dateTime) {
        LocalDateTime t = (dateTime == null) ? LocalDateTime.now() : dateTime;
        return monthOfWeek(t.toLocalDate());
    }

    public String extractMonth(Instant instant) {
        Instant i = (instant == null) ? Instant.now() : instant;
        return monthOfWeek(i.atZone(VN_ZONE).toLocalDate());
    }

    public String getWeekString(ZonedDateTime dateTime) {
        if (dateTime == null) dateTime = ZonedDateTime.now();
        java.time.temporal.WeekFields weekFields = java.time.temporal.WeekFields.ISO;
        int weekNumber = dateTime.get(weekFields.weekOfWeekBasedYear());
        int year = dateTime.get(weekFields.weekBasedYear());
        return String.format("%d-W%02d", year, weekNumber);
    }

    /**
     * Chỉ tiêu KPI của tháng = số TUẦN thuộc tháng đó × 100.
     *
     * <p>Một tuần thuộc tháng nào được xác định bằng ngày thứ Hai của tuần
     * (xem {@link #monthOfWeek}), nên đếm số ngày thứ Hai trong tháng chính là
     * đếm số tuần: tháng 4 tuần → 400 điểm, tháng 5 tuần → 500 điểm.
     */
    public int getMaxKpiForMonth(String monthStr) {
        try {
            java.time.YearMonth ym = java.time.YearMonth.parse(monthStr, MONTH_FORMATTER);
            int mondays = 0;
            for (int i = 1; i <= ym.lengthOfMonth(); i++) {
                if (ym.atDay(i).getDayOfWeek() == java.time.DayOfWeek.MONDAY) {
                    mondays++;
                }
            }
            return mondays * 100;
        } catch (Exception e) {
            return 400; // fallback
        }
    }

    /**
     * Danh sách mã tuần (yyyy-Www) thuộc một tháng KPI, theo thứ tự.
     * Mỗi phần tử ứng với một cột "Tuần N" trên báo cáo.
     */
    public List<String> getWeeksOfMonth(String monthStr) {
        List<String> weeks = new java.util.ArrayList<>();
        try {
            java.time.YearMonth ym = java.time.YearMonth.parse(monthStr, MONTH_FORMATTER);
            for (int i = 1; i <= ym.lengthOfMonth(); i++) {
                java.time.LocalDate d = ym.atDay(i);
                if (d.getDayOfWeek() == java.time.DayOfWeek.MONDAY) {
                    java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.ISO;
                    weeks.add(String.format("%d-W%02d",
                            d.get(wf.weekBasedYear()), d.get(wf.weekOfWeekBasedYear())));
                }
            }
        } catch (Exception ignored) {
            // tháng không hợp lệ → trả danh sách rỗng
        }
        return weeks;
    }

    @Transactional
    public KpiScore updateKpiPoints(Long userId, String type, int points, ZonedDateTime submittedAt) {
        String month = extractMonth(submittedAt);
        String week = getWeekString(submittedAt);
        String lowerType = type.toLowerCase();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng có ID: " + userId));

        KpiScore kpiScore = kpiScoreRepository.findByUserIdAndMonth(userId, month)
                .orElseGet(() -> KpiScore.builder()
                        .user(user)
                        .month(month)
                        .attendance(0)
                        .meeting(0)
                        .post(0)
                        .deal(0)
                        .total(0)
                        .isFlagged(false)
                        .build());

        if (lowerType.equals("deal")) {
            kpiScore.setDeal(Math.max(0, kpiScore.getDeal() + points));
        } else {
            // Update weekly score capped at 100
            KpiWeeklyScore weeklyScore = kpiWeeklyScoreRepository.findByUserIdAndWeek(userId, week)
                    .orElseGet(() -> KpiWeeklyScore.builder()
                            .user(user)
                            .month(month)
                            .week(week)
                            .attendance(0)
                            .meeting(0)
                            .post(0)
                            .total(0)
                            .build());

            switch (lowerType) {
                case "attendance":
                    weeklyScore.setAttendance(Math.max(0, weeklyScore.getAttendance() + points));
                    break;
                case "meeting":
                    weeklyScore.setMeeting(Math.max(0, weeklyScore.getMeeting() + points));
                    break;
                case "post":
                    weeklyScore.setPost(Math.max(0, weeklyScore.getPost() + points));
                    break;
                default:
                    throw new IllegalArgumentException("Loại điểm KPI không hợp lệ: " + type);
            }

            int rawWeekly = weeklyScore.getAttendance() + weeklyScore.getMeeting() + weeklyScore.getPost();
            weeklyScore.setTotal(Math.min(100, rawWeekly)); // Cap at 100 points per week
            kpiWeeklyScoreRepository.save(weeklyScore);
        }

        // Recalculate Monthly total based on Weekly Scores + Deal
        List<KpiWeeklyScore> weeklyScores = kpiWeeklyScoreRepository.findByUserIdAndMonth(userId, month);
        
        int sumAttendance = 0;
        int sumMeeting = 0;
        int sumPost = 0;
        int sumWeeklyTotal = 0;

        for (KpiWeeklyScore ws : weeklyScores) {
            sumAttendance += ws.getAttendance();
            sumMeeting += ws.getMeeting();
            sumPost += ws.getPost();
            sumWeeklyTotal += ws.getTotal();
        }

        kpiScore.setAttendance(sumAttendance);
        kpiScore.setMeeting(sumMeeting);
        kpiScore.setPost(sumPost);

        int maxKpi = getMaxKpiForMonth(month);
        
        // Final Monthly total = Sum of weekly capped totals + Deal points
        // The overall total is capped at maxKpi
        int rawTotal = sumWeeklyTotal + kpiScore.getDeal();
        kpiScore.setTotal(Math.min(maxKpi, rawTotal));

        KpiScore savedScore = kpiScoreRepository.save(kpiScore);
        
        int currentWeeklyTotal = kpiWeeklyScoreRepository.findByUserIdAndWeek(userId, getWeekString(ZonedDateTime.now()))
                .map(KpiWeeklyScore::getTotal)
                .orElse(0);

        try {
            messagingTemplate.convertAndSend(
                "/topic/kpi/" + userId, 
                (Object) Map.of("status", "SUCCESS", "data", KpiScoreResponseDTO.from(savedScore, currentWeeklyTotal, maxKpi))
            );
        } catch (Exception e) {
            // Ignore messaging errors
        }
        
        return savedScore;
    }
}
