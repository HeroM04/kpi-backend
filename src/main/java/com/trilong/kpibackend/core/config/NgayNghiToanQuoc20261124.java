package com.trilong.kpibackend.core.config;

import com.trilong.kpibackend.modules.attendance.service.NgayNghiLeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 24/11/2026 là ngày nghỉ của toàn quốc nhưng hệ thống chưa có danh sách ngày lễ,
 * tác vụ chốt cuối ngày chấm 144 người vắng không phép. Người dùng yêu cầu hoàn
 * lại điểm cho toàn bộ nhân sự (25/11/2026).
 *
 * <p>Thêm 24/11/2026 vào danh sách ngày nghỉ lễ → {@link NgayNghiLeService#them}
 * hoàn đúng số đã trừ và xóa các bản ghi vắng không phép của ngày đó. Chạy trước
 * tác vụ "chốt bù hôm qua lúc khởi động" (trễ 90 giây) nên ngày này không bị chốt
 * lại. Mỗi lần khởi động đều chạy nhưng chỉ có tác dụng lần đầu: lần sau không
 * còn bản ghi vắng nào để hoàn. Lỗi thì chỉ ghi log, không chặn khởi động.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NgayNghiToanQuoc20261124 implements CommandLineRunner {

    static final LocalDate NGAY = LocalDate.of(2026, 11, 24);

    private final NgayNghiLeService ngayNghiLeService;
    private final com.trilong.kpibackend.modules.attendance.repository.LeaveRequestRepository leaveRequestRepository;

    @Override
    public void run(String... args) {
        try {
            // Chỉ làm khi ngày đó còn vắng không phép: làm xong một lần thì thôi, Admin
            // có xóa / đổi tên ngày lễ trên web sau này cũng không bị thêm lại
            boolean conVang = leaveRequestRepository.findByLeaveDate(NGAY).stream()
                    .anyMatch(r -> "UNEXCUSED".equals(r.getStatus()));
            if (!conVang) return;
            var kq = ngayNghiLeService.them(NGAY, ngayNghiLeService.laNgayLe(NGAY) ? null : "Ngày nghỉ toàn quốc");
            if (kq.soNguoiHoan() > 0) {
                log.info("[Ngày nghỉ 24/11/2026] Đã hoàn điểm vắng cho {} nhân sự, tổng {}đ.", kq.soNguoiHoan(), kq.tongDiemHoan());
            }
        } catch (Exception e) {
            log.error("[Ngày nghỉ 24/11/2026] Lỗi: {}", e.getMessage(), e);
        }
    }
}
