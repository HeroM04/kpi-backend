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

    public String extractMonth(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return MONTH_FORMATTER.format(ZonedDateTime.now());
        }
        return MONTH_FORMATTER.format(dateTime);
    }

    public String extractMonth(LocalDateTime dateTime) {
        if (dateTime == null) {
            return MONTH_FORMATTER.format(LocalDateTime.now());
        }
        return MONTH_FORMATTER.format(dateTime);
    }

    public String extractMonth(Instant instant) {
        if (instant == null) {
            return MONTH_FORMATTER.withZone(ZoneId.systemDefault()).format(Instant.now());
        }
        return MONTH_FORMATTER.withZone(ZoneId.systemDefault()).format(instant);
    }

    public String getWeekString(ZonedDateTime dateTime) {
        if (dateTime == null) dateTime = ZonedDateTime.now();
        java.time.temporal.WeekFields weekFields = java.time.temporal.WeekFields.ISO;
        int weekNumber = dateTime.get(weekFields.weekOfWeekBasedYear());
        int year = dateTime.get(weekFields.weekBasedYear());
        return String.format("%d-W%02d", year, weekNumber);
    }

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
        
        try {
            messagingTemplate.convertAndSend(
                "/topic/kpi/" + userId, 
                (Object) Map.of("status", "SUCCESS", "data", KpiScoreResponseDTO.from(savedScore))
            );
        } catch (Exception e) {
            // Ignore messaging errors
        }
        
        return savedScore;
    }
}
