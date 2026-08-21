package com.trilong.kpibackend.modules.training.scheduler;

import com.trilong.kpibackend.modules.kpi.entity.KpiAutoGrant;
import com.trilong.kpibackend.modules.kpi.repository.KpiAutoGrantRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.training.repository.TrainingSessionRepository;
import com.trilong.kpibackend.modules.training.service.TrainingService;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;

/**
 * Tuần không có lịch đào tạo → mọi người mặc định được 15đ.
 *
 * <p>Theo bảng tiêu chí, mục "Học tập, Đào tạo" tối đa 15đ/tuần. Nếu tuần đó
 * công ty không tổ chức buổi đào tạo nào thì nhân sự không có cơ hội kiếm điểm,
 * nên được cộng thẳng 15đ thay vì bị mất trắng.
 *
 * <p>Chạy Chủ nhật 23:45 (giờ VN) cho tuần vừa khép lại. Mỗi lần cộng đều ghi
 * một bản ghi {@link KpiAutoGrant} nên chạy lại không bị cộng trùng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoTrainingWeekScheduler {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String GRANT_TYPE = "NO_TRAINING_WEEK";

    private final TrainingSessionRepository trainingSessionRepository;
    private final KpiAutoGrantRepository kpiAutoGrantRepository;
    private final KpiCalculationService kpiCalculationService;
    private final UserRepository userRepository;

    @Scheduled(cron = "0 45 23 * * SUN", zone = "Asia/Ho_Chi_Minh")
    public void grantForClosingWeek() {
        try {
            int n = grantIfNoTraining(LocalDate.now(VN_ZONE));
            log.info("[Scheduler] Kiểm tra tuần không có đào tạo — đã cộng cho {} nhân sự.", n);
        } catch (Exception e) {
            log.error("[Scheduler] Lỗi khi cộng điểm tuần không có đào tạo: {}", e.getMessage(), e);
        }
    }

    /**
     * Cộng 15đ cho tuần chứa {@code anyDayOfWeek} nếu tuần đó công ty không tổ
     * chức buổi đào tạo nào.
     *
     * @return số nhân sự được cộng ở lần chạy này
     */
    @Transactional
    public int grantIfNoTraining(LocalDate anyDayOfWeek) {
        LocalDate monday = anyDayOfWeek.with(WeekFields.ISO.dayOfWeek(), 1);
        ZonedDateTime from = monday.atStartOfDay(VN_ZONE);
        ZonedDateTime to = monday.plusDays(7).atStartOfDay(VN_ZONE);

        long held = trainingSessionRepository.countHeldSessionsBetween(from, to);
        if (held > 0) {
            log.info("[Đào tạo] Tuần {} có {} buổi đào tạo — không cộng điểm mặc định.", monday, held);
            return 0;
        }

        // Mốc thời gian giữa tuần để getWeekString rơi đúng tuần cần cộng
        ZonedDateTime midWeek = monday.plusDays(2).atTime(12, 0).atZone(VN_ZONE);
        String week = kpiCalculationService.getWeekString(midWeek);

        int count = 0;
        for (User user : userRepository.findAll()) {
            if (!"ACTIVE".equals(user.getStatus())) continue;
            if (user.getCreatedAt() != null
                    && user.getCreatedAt().withZoneSameInstant(VN_ZONE).toLocalDate().isAfter(monday.plusDays(6))) {
                continue; // vào công ty sau tuần này
            }
            if (kpiAutoGrantRepository.existsByUserIdAndPeriodAndGrantType(user.getId(), week, GRANT_TYPE)) {
                continue;
            }

            kpiCalculationService.updateKpiPoints(user.getId(), "attendance",
                    TrainingService.CAP_TRAINING_PER_WEEK, midWeek,
                    "Tuần này công ty không tổ chức đào tạo — cộng mặc định theo quy định");

            KpiAutoGrant grant = new KpiAutoGrant();
            grant.setUserId(user.getId());
            grant.setPeriod(week);
            grant.setGrantType(GRANT_TYPE);
            grant.setCategory("attendance");
            grant.setPoints(TrainingService.CAP_TRAINING_PER_WEEK);
            grant.setReason("Tuần " + week + " công ty không tổ chức đào tạo — cộng mặc định "
                    + TrainingService.CAP_TRAINING_PER_WEEK + "đ theo quy định");
            kpiAutoGrantRepository.save(grant);
            count++;
        }

        log.info("[Đào tạo] Tuần {} không có buổi đào tạo nào — cộng {}đ cho {} nhân sự.",
                week, TrainingService.CAP_TRAINING_PER_WEEK, count);
        return count;
    }
}
