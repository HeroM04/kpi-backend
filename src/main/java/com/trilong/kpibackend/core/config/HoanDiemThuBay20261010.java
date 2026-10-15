package com.trilong.kpibackend.core.config;

import com.trilong.kpibackend.modules.attendance.service.HoanDiemThuBayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Sửa dữ liệu một lần: hoàn điểm đi muộn đã trừ ngày thứ Bảy 10/10/2026
 * (người dùng yêu cầu 15/10/2026 khi chốt "thứ Bảy không tính điểm chuyên cần").
 *
 * <p>Chạy mỗi lần máy chủ khởi động nhưng chỉ có tác dụng lần đầu — dịch vụ
 * tự trừ phần đã hoàn nên không bao giờ hoàn trùng. Lỗi thì chỉ ghi log, không
 * chặn máy chủ khởi động.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoanDiemThuBay20261010 implements CommandLineRunner {

    private final HoanDiemThuBayService hoanDiemThuBayService;

    @Override
    public void run(String... args) {
        try {
            hoanDiemThuBayService.hoanDiemDiMuon(LocalDate.of(2026, 10, 10));
        } catch (Exception e) {
            log.error("[Hoàn điểm thứ Bảy 10/10/2026] Lỗi: {}", e.getMessage(), e);
        }
    }
}
