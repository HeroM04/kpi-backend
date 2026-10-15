package com.trilong.kpibackend.core.config;

import com.trilong.kpibackend.modules.attendance.service.HoanDiemThuBayService;
import com.trilong.kpibackend.modules.attendance.service.LeaveRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Sửa dữ liệu một lần theo luật thứ Bảy (người dùng chốt 15/10/2026: thứ Bảy
 * vẫn chấm công bình thường, chỉ không cộng / trừ điểm KPI):
 * <ul>
 *   <li>Thứ Bảy 10/10/2026: hoàn điểm đi muộn đã trừ, thu lại điểm đúng giờ
 *       đã cộng, hoàn điểm vắng (−15 / −10) đã trừ.</li>
 *   <li>Chốt lại vắng ngày 10/10 (không trừ điểm) để bảng công chắc chắn còn
 *       dấu không phép — bản 05c923c từng gỡ dấu này nếu đã lỡ deploy.</li>
 *   <li>Đơn nghỉ thứ Bảy đã duyệt từ 17/10 trở đi: hoàn −10đ.</li>
 * </ul>
 *
 * <p>Chạy mỗi lần máy chủ khởi động nhưng chỉ có tác dụng lần đầu — các hàm
 * tự trừ phần đã làm nên không bao giờ trùng. Lỗi thì chỉ ghi log, không chặn
 * máy chủ khởi động.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoanDiemThuBay20261010 implements CommandLineRunner {

    private static final LocalDate THU_BAY = LocalDate.of(2026, 10, 10);

    private final HoanDiemThuBayService hoanDiemThuBayService;
    private final LeaveRequestService leaveRequestService;

    @Override
    public void run(String... args) {
        try {
            hoanDiemThuBayService.chuanHoa(THU_BAY);
        } catch (Exception e) {
            log.error("[Chuẩn hóa điểm thứ Bảy 10/10/2026] Lỗi: {}", e.getMessage(), e);
        }
        try {
            leaveRequestService.closeDay(THU_BAY);
        } catch (Exception e) {
            log.error("[Chốt lại vắng 10/10/2026] Lỗi: {}", e.getMessage(), e);
        }
        try {
            leaveRequestService.hoanDonNghiThuBay(LocalDate.of(2026, 10, 11));
        } catch (Exception e) {
            log.error("[Hoàn đơn nghỉ thứ Bảy] Lỗi: {}", e.getMessage(), e);
        }
    }
}
