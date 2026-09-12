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

    // Chốt sổ lúc 23:59 — hết ngày mới kết sổ, ai chấm công muộn trong ngày vẫn
    // kịp được ghi nhận trước khi hệ thống chấm vắng không phép.
    @Scheduled(cron = "0 59 23 * * *", zone = "Asia/Ho_Chi_Minh")
    public void closeToday() {
        chot(LocalDate.now(VN_ZONE), "Scheduled 23:59");
    }

    /**
     * Chạy bù HÔM QUA một lần sau khi máy chủ khởi động.
     *
     * <p>Lịch 23:59 chỉ chạy khi máy chủ đang thức. Trên Render gói miễn phí,
     * dịch vụ ngủ sau 15 phút không ai gọi — buổi tối gần như chắc chắn ngủ,
     * nên ngày hôm đó không được chốt: ai không chấm công cũng không bị ghi
     * vắng, và không ai biết vì không có lỗi nào được ghi ra. Deploy bản mới
     * cũng khởi động lại giữa chừng và có thể trượt đúng mốc 23:59.
     *
     * <p>Chỉ bù đúng một ngày (hôm qua), không lùi xa hơn: {@code closeDay}
     * không chấm trùng, nhưng bù nhiều ngày một lúc sẽ làm điểm trừ của cả
     * tuần trước đột ngột đổ về trong một buổi sáng — nhân sự không hiểu vì
     * sao. Chủ nhật {@code closeDay} tự bỏ qua.
     */
    @Scheduled(initialDelay = 90_000, fixedDelay = Long.MAX_VALUE)
    public void closeYesterdayOnStartup() {
        chot(LocalDate.now(VN_ZONE).minusDays(1), "Startup, chạy bù hôm qua");
    }

    private void chot(LocalDate ngay, String nguon) {
        try {
            int count = leaveRequestService.closeDay(ngay);
            log.info("[Scheduler][{}] Chốt chấm công ngày {} — {} nhân sự vắng không phép.", nguon, ngay, count);
        } catch (Exception e) {
            log.error("[Scheduler][{}] Lỗi khi chốt chấm công ngày {}: {}", nguon, ngay, e.getMessage(), e);
        }
    }
}
