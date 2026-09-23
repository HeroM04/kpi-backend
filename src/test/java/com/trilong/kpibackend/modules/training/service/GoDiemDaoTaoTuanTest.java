package com.trilong.kpibackend.modules.training.service;

import com.trilong.kpibackend.modules.kpi.entity.KpiLedgerEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gỡ điểm đào tạo tuần cộng sớm: chấm lại tuần từ nhật ký, bỏ mọi dòng của cơ
 * chế đào tạo tuần.
 *
 * <p>Đây là thao tác đụng vào điểm của cả công ty. Sai theo hướng lỏng thì có
 * người còn giữ điểm cộng sai; sai theo hướng chặt thì mất luôn điểm chấm công
 * hoặc điểm đào tạo 1-1 hợp lệ.
 */
class GoDiemDaoTaoTuanTest {

    private long id = 1;

    private KpiLedgerEntry dong(String dienGiai, int points) {
        KpiLedgerEntry e = new KpiLedgerEntry();
        e.setId(id++);
        e.setUserId(7L);
        e.setCategory("attendance");
        e.setPoints(points);
        e.setReason(dienGiai);
        return e;
    }

    @Test
    @DisplayName("Nhận đúng các dòng của cơ chế đào tạo tuần, kể cả câu chữ bản cũ")
    void nhanDungDongDaoTaoTuan() {
        assertTrue(TrainingService.laDongDiemDaoTaoTuan("Tuần này công ty không tổ chức đào tạo — cộng mặc định theo quy định"));
        assertTrue(TrainingService.laDongDiemDaoTaoTuan("Thiếu buổi đào tạo bắt buộc trong tuần — thu hồi điểm đã cộng"));
        assertTrue(TrainingService.laDongDiemDaoTaoTuan("Đã dự đủ 2 buổi đào tạo bắt buộc của tuần"));
        assertTrue(TrainingService.laDongDiemDaoTaoTuan("Thu hồi điểm đào tạo tuần đã cộng sớm — điểm đào tạo chỉ chốt vào tối Chủ nhật"));
    }

    @Test
    @DisplayName("KHÔNG đụng điểm đào tạo 1-1, chấm công hay vắng mặt")
    void khongDungDongKhac() {
        assertFalse(TrainingService.laDongDiemDaoTaoTuan("Báo cáo đào tạo 1-1"));
        assertFalse(TrainingService.laDongDiemDaoTaoTuan("Admin duyệt đào tạo 1-1 — tele sales"));
        assertFalse(TrainingService.laDongDiemDaoTaoTuan("Chấm công vào lúc 08:12"));
        assertFalse(TrainingService.laDongDiemDaoTaoTuan("Vắng không phép ngày 22/09 — không chấm công và không có đơn xin nghỉ"));
        assertFalse(TrainingService.laDongDiemDaoTaoTuan(null));
    }

    @Test
    @DisplayName("Trường hợp trong ảnh: +15, gỡ −15 bị sàn 0đ nuốt, lại +15 — gỡ sạch cả hai lần cộng")
    void truongHopCongGoCong() {
        List<KpiLedgerEntry> tuan = new ArrayList<>();
        tuan.add(dong("Chấm công vào lúc 08:30", 15));
        tuan.add(dong("Vắng không phép ngày 21/09 — không chấm công và không có đơn xin nghỉ", -15));
        tuan.add(dong("Vắng không phép ngày 22/09 — không chấm công và không có đơn xin nghỉ", -15));
        tuan.add(dong("Tuần này công ty không tổ chức đào tạo — cộng mặc định theo quy định", 15));
        tuan.add(dong("Thiếu buổi đào tạo bắt buộc trong tuần — thu hồi điểm đã cộng", -15));
        tuan.add(dong("Tuần này công ty không tổ chức đào tạo — cộng mặc định theo quy định", 15));

        // Không có đào tạo: +15, −15 → 0, −15 → vẫn 0 (sàn). Điểm đúng = 0.
        assertEquals(0, TrainingService.diemTuanKhongTinhDaoTao(tuan));
    }

    @Test
    @DisplayName("Giữ nguyên điểm chấm công, chỉ bỏ phần đào tạo")
    void giuDiemChamCong() {
        List<KpiLedgerEntry> tuan = List.of(
                dong("Chấm công vào lúc 08:20", 5),
                dong("Tuần này công ty không tổ chức đào tạo — cộng mặc định theo quy định", 15),
                dong("Chấm công vào lúc 08:31", 5),
                dong("Chấm công vào lúc 08:40", 5));
        assertEquals(15, TrainingService.diemTuanKhongTinhDaoTao(tuan));
    }

    @Test
    @DisplayName("Kẹp trần 30 đúng thứ tự như lúc cộng thật")
    void kepTran() {
        List<KpiLedgerEntry> tuan = List.of(
                dong("Chấm công vào lúc 08:20", 20),
                dong("Chấm công vào lúc 08:20", 20),   // chạm trần 30
                dong("Vắng không phép ngày 24/09 — không chấm công và không có đơn xin nghỉ", -15));
        assertEquals(15, TrainingService.diemTuanKhongTinhDaoTao(tuan));
    }
}
