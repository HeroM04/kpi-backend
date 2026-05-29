package com.trilong.kpibackend.modules.training.scheduler;

import com.trilong.kpibackend.modules.training.entity.TrainingSession;
import com.trilong.kpibackend.modules.training.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Scheduler tự động kết thúc các buổi đào tạo quá ngày.
 * - Chạy mỗi ngày lúc 00:05 (5 phút sau nửa đêm) → đánh dấu COMPLETED
 *   tất cả buổi UPCOMING có startTime trước đầu ngày hôm nay.
 * - Cũng chạy khi app khởi động (1 phút sau start) để xử lý trường hợp
 *   server bị tắt qua đêm.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrainingAutoCompleteScheduler {

    private final TrainingSessionRepository trainingSessionRepository;

    /**
     * Chạy mỗi ngày lúc 00:05 giờ Việt Nam (GMT+7).
     * Cron: 0 5 0 * * * = giây 0, phút 5, giờ 0, mỗi ngày.
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void autoCompleteExpiredSessions() {
        doAutoComplete("Scheduled (00:05)");
    }

    /**
     * Chạy 1 lần sau khi server khởi động 60 giây
     * — xử lý trường hợp server bị tắt qua đêm và restart vào buổi sáng.
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = Long.MAX_VALUE)
    @Transactional
    public void autoCompleteOnStartup() {
        doAutoComplete("Startup check");
    }

    private void doAutoComplete(String trigger) {
        // Tính đầu ngày hôm nay theo giờ Việt Nam
        ZonedDateTime todayStart = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .toLocalDate()
                .atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh"));

        List<TrainingSession> expired = trainingSessionRepository.findExpiredUpcomingSessions(todayStart);

        if (expired.isEmpty()) {
            log.info("[TrainingScheduler][{}] Không có buổi học nào cần tự động kết thúc.", trigger);
            return;
        }

        for (TrainingSession session : expired) {
            session.setStatus("COMPLETED");
            log.info("[TrainingScheduler][{}] Tự động kết thúc buổi học: id={}, title='{}', startTime={}",
                    trigger, session.getId(), session.getTitle(), session.getStartTime());
        }

        trainingSessionRepository.saveAll(expired);
        log.info("[TrainingScheduler][{}] Đã tự động kết thúc {} buổi học quá hạn.", trigger, expired.size());
    }
}
