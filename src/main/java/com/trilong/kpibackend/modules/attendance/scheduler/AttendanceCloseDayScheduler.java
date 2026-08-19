package com.trilong.kpibackend.modules.attendance.scheduler;

import com.trilong.kpibackend.modules.attendance.service.LeaveRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Chốt vắng mặt cuối ngày.
 *
 * <p>23:30 mỗi ngày (giờ VN), rà lại ngày vừa qua: nhân sự nào không có bản ghi
 * chấm công và cũng không có đơn xin vắng được duyệt thì bị chấm
 * <b>vắng không phép (−15đ)</b>. Ai có đơn được duyệt đã bị trừ 10đ ngay lúc
 * Admin bấm duyệt nên không xử lý lại ở đây.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceCloseDayScheduler {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final LeaveRequestService leaveRequestService;

    @Scheduled(cron = "0 30 23 * * *", zone = "Asia/Ho_Chi_Minh")
    public void closeToday() {
        LocalDate today = LocalDate.now(VN_ZONE);
        try {
            int count = leaveRequestService.closeDay(today);
            log.info("[Scheduler] Chốt chấm công ngày {} — {} nhân sự vắng không phép.", today, count);
        } catch (Exception e) {
            log.error("[Scheduler] Lỗi khi chốt chấm công ngày {}: {}", today, e.getMessage(), e);
        }
    }
}
