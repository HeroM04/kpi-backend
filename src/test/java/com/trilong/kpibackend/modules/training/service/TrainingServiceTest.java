package com.trilong.kpibackend.modules.training.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hai mốc thời gian quyết định điểm đào tạo tuần của cả công ty.
 *
 * <p>Sai một chút ở đây là hỏng theo kiểu không ai phát hiện ngay: mốc chốt tuần
 * đặt ở nửa đêm thì hai tác vụ chạy lúc 23:45 và 23:55 Chủ nhật đều coi tuần
 * chưa khép, cả công ty không ai được 15đ của tuần không có đào tạo. Còn mốc ghi
 * sổ rơi ra ngoài tuần thì khoản điểm nhảy sang kỳ khác.
 */
class TrainingServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Tuần 39/2026: thứ Hai 21/09 → Chủ nhật 27/09. */
    private static final LocalDate THU_HAI = LocalDate.of(2026, 9, 21);

    private ZonedDateTime luc(int ngay, int gio, int phut) {
        return ZonedDateTime.of(2026, 9, ngay, gio, phut, 0, 0, VN);
    }

    @Test
    @DisplayName("Giữa tuần thì tuần chưa khép")
    void giuaTuanChuaKhep() {
        assertFalse(TrainingService.tuanDaKhep(THU_HAI, luc(21, 8, 0)),  "sáng thứ Hai");
        assertFalse(TrainingService.tuanDaKhep(THU_HAI, luc(23, 23, 59)), "khuya thứ Tư");
        assertFalse(TrainingService.tuanDaKhep(THU_HAI, luc(27, 22, 0)),  "tối Chủ nhật, trước 23:00");
    }

    @Test
    @DisplayName("Hai tác vụ chốt tuần lúc 23:45 và 23:55 Chủ nhật phải thấy tuần đã khép")
    void tacVuChotTuanThayTuanDaKhep() {
        assertTrue(TrainingService.tuanDaKhep(THU_HAI, luc(27, 23, 45)), "cron chốt tuần");
        assertTrue(TrainingService.tuanDaKhep(THU_HAI, luc(27, 23, 55)), "cron cuối ngày");
        assertTrue(TrainingService.tuanDaKhep(THU_HAI, luc(28, 23, 55)), "chấm bù đêm thứ Hai tuần sau");
    }

    @Test
    @DisplayName("Đang trong tuần thì ghi sổ đúng lúc chấm")
    void ghiSoTheoLucCham() {
        ZonedDateTime bayGio = luc(23, 19, 30);
        assertEquals(bayGio, TrainingService.mocGhiSo(THU_HAI, bayGio));
    }

    @Test
    @DisplayName("Chấm bù tuần đã qua thì neo vào trưa thứ Tư của chính tuần đó")
    void ghiSoKhiChamBu() {
        // Đêm thứ Hai tuần sau (28/09) chấm bù cho tuần 21–27/09
        ZonedDateTime moc = TrainingService.mocGhiSo(THU_HAI, luc(28, 23, 55));
        assertEquals(LocalDate.of(2026, 9, 23), moc.toLocalDate(), "phải rơi vào thứ Tư 23/09");
        assertEquals(12, moc.getHour());

        // Và luôn nằm trong tuần, không lệch sang kỳ khác
        assertFalse(moc.toLocalDate().isBefore(THU_HAI));
        assertTrue(moc.toLocalDate().isBefore(THU_HAI.plusDays(7)));
    }
}
