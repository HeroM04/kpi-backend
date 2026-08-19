package com.trilong.kpibackend.modules.user.scheduler;

import com.trilong.kpibackend.modules.user.service.ReferralRewardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mỗi ngày 23:20 (giờ VN) rà điểm "gieo hạt nhân sự mới": ai giới thiệu người
 * vào công ty và người đó đã làm đủ một tháng thì cộng 15đ cho người giới thiệu.
 *
 * <p>Chạy trước bộ chốt vắng mặt (23:30) để điểm của ngày được ghi trọn vẹn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReferralRewardScheduler {

    private final ReferralRewardService referralRewardService;

    @Scheduled(cron = "0 20 23 * * *", zone = "Asia/Ho_Chi_Minh")
    public void grantMaturedReferrals() {
        try {
            var result = referralRewardService.grantMaturedReferrals();
            if (result.granted() > 0) {
                log.info("[Scheduler] Gieo hạt nhân sự mới — đã cộng cho {} người giới thiệu.", result.granted());
            }
        } catch (Exception e) {
            log.error("[Scheduler] Lỗi khi cộng điểm gieo hạt nhân sự mới: {}", e.getMessage(), e);
        }
    }
}
