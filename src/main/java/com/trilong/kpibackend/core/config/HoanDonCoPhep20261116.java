package com.trilong.kpibackend.core.config;

import com.trilong.kpibackend.modules.attendance.service.LeaveRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Sửa dữ liệu một lần theo luật nghỉ phép mới (người dùng chốt 16/11/2026: đơn
 * xin từ hôm trước ngày nghỉ được duyệt thì không trừ điểm; gửi ngay trong ngày
 * nghỉ vẫn −10đ). Người dùng chốt thêm: mọi đơn đã duyệt và đã bị trừ −10đ theo
 * luật cũ đều được hoàn, không giới hạn ngày — xem
 * {@link LeaveRequestService#hoanDonCoPhepLuatCu()}.
 *
 * <p>Chạy mỗi lần máy chủ khởi động nhưng chỉ có tác dụng lần đầu — đơn đã hoàn
 * thì cờ đã trừ về false, khoản trừ theo luật mới thì được nhận ra và giữ. Lỗi thì
 * chỉ ghi log, không chặn máy chủ khởi động.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoanDonCoPhep20261116 implements CommandLineRunner {

    private final LeaveRequestService leaveRequestService;

    @Override
    public void run(String... args) {
        try {
            leaveRequestService.hoanDonCoPhepLuatCu();
        } catch (Exception e) {
            log.error("[Hoàn điểm đơn nghỉ có phép luật cũ] Lỗi: {}", e.getMessage(), e);
        }
    }
}
