package com.trilong.kpibackend.modules.training.service;

import com.trilong.kpibackend.core.utils.GioVN;
import com.trilong.kpibackend.modules.training.dto.TrainingAttendeeResponseDTO;
import com.trilong.kpibackend.modules.training.entity.TrainingAttendee;
import com.trilong.kpibackend.modules.training.entity.TrainingRsvp;
import com.trilong.kpibackend.modules.training.entity.TrainingSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lịch sử đào tạo "của tôi" trên app.
 *
 * <p>Trước đây mỗi dòng chỉ hiện "Buổi đào tạo" vì máy chủ không gửi tên buổi.
 * Giờ có thêm ghi chú vắng (đã học rồi, bận việc…) để Admin duyệt — dòng chưa
 * được tính chỉ trả về khi app mới hỏi kèm, để app cũ không hiện nhầm thành
 * "đã tham gia" có dấu tích xanh.
 */
class TrainingLichSuTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Long TOI = 7L;

    private static ZonedDateTime luc(int ngay, int gio) {
        return ZonedDateTime.of(2026, 11, ngay, gio, 0, 0, 0, VN);
    }

    private static TrainingSession buoi(long id, String ten, String loai, ZonedDateTime batDau) {
        TrainingSession s = TrainingSession.builder().title(ten).presenter("Anh Long").trainingType(loai).startTime(batDau).build();
        s.setId(id);
        return s;
    }

    private static TrainingAttendee diemDanh(long buoi, ZonedDateTime luc, String nguon) {
        return TrainingAttendee.builder().sessionId(buoi).userId(TOI).attendedAt(luc).source(nguon).build();
    }

    private static TrainingRsvp ghiChu(long buoi, String lyDo, String trangThai) {
        TrainingRsvp r = new TrainingRsvp();
        r.setSessionId(buoi);
        r.setUserId(TOI);
        r.setChoice("DECLINE");
        r.setReason(lyDo);
        r.setStatus(trangThai);
        r.setCreatedAt(luc(16, 20));
        return r;
    }

    private static final Map<Long, TrainingSession> BUOI = Map.of(
            1L, buoi(1, "Kỹ năng chốt sale", "SKILL", luc(4, 10)),
            2L, buoi(2, "Dự án Vinhomes Ocean Park 3", "PROJECT", luc(17, 9)),
            3L, buoi(3, "Telesales cơ bản", "SKILL", luc(17, 14)),
            4L, buoi(4, "Pháp lý căn hộ", "SKILL", luc(18, 9)));

    /** Cả tháng 11/2026. */
    private static boolean thang11(java.time.LocalDate d) {
        return d.getYear() == 2026 && d.getMonthValue() == 11;
    }

    @Test
    @DisplayName("Buổi đã điểm danh có tên buổi, người hướng dẫn, loại buổi")
    void coTenBuoi() {
        var ds = TrainingService.ghepLichSu(List.of(diemDanh(1, luc(4, 10).plusMinutes(51), "REAL")),
                List.of(), BUOI, TrainingLichSuTest::thang11, false);
        assertEquals(1, ds.size());
        TrainingAttendeeResponseDTO d = ds.get(0);
        assertEquals("Kỹ năng chốt sale", d.getSessionTitle());
        assertEquals("Anh Long", d.getPresenter());
        assertEquals("SKILL", d.getTrainingType());
        assertEquals("REAL", d.getSource());
        assertNull(d.getNote());
    }

    @Test
    @DisplayName("Ghi chú đã duyệt (được miễn) hiện kèm lý do")
    void mienKemLyDo() {
        var ds = TrainingService.ghepLichSu(List.of(diemDanh(3, luc(17, 15), "EXEMPT")),
                List.of(ghiChu(3, "Em đã học buổi này tháng trước", "APPROVED")), BUOI, TrainingLichSuTest::thang11, true);
        assertEquals(1, ds.size(), "đã được tính thì không thêm dòng ghi chú riêng");
        assertEquals("EXEMPT", ds.get(0).getSource());
        assertEquals("Em đã học buổi này tháng trước", ds.get(0).getNote());
        assertEquals("APPROVED", ds.get(0).getNoteStatus());
    }

    @Test
    @DisplayName("Ghi chú chờ duyệt / bị từ chối chỉ có khi app hỏi kèm ghi chú")
    void ghiChuChuaTinh() {
        var ghi = List.of(ghiChu(2, "Không bán dự án này", "PENDING"), ghiChu(4, "Bận đi gặp khách", "REJECTED"));
        assertTrue(TrainingService.ghepLichSu(List.of(), ghi, BUOI, TrainingLichSuTest::thang11, false).isEmpty(),
                "app cũ: không có dòng nào, tránh hiện nhầm là đã tham gia");

        var ds = TrainingService.ghepLichSu(List.of(), ghi, BUOI, TrainingLichSuTest::thang11, true);
        assertEquals(2, ds.size());
        // Mới nhất đứng trên: buổi 18/11 rồi tới 17/11
        assertEquals("Pháp lý căn hộ", ds.get(0).getSessionTitle());
        assertEquals("REJECTED", ds.get(0).getNoteStatus());
        assertEquals("NOTE", ds.get(1).getSource());
        assertNull(ds.get(1).getAttendedAt());
        assertEquals(luc(17, 9), ds.get(1).getSessionStartTime());
        assertEquals("PROJECT", ds.get(1).getTrainingType());
    }

    @Test
    @DisplayName("Ghi chú lọc theo ngày của buổi học, không theo ngày gửi")
    void locTheoNgayBuoiHoc() {
        // Gửi tối 16/11 cho buổi sáng 17/11
        var ghi = List.of(ghiChu(2, "Không bán dự án này", "PENDING"));
        assertTrue(TrainingService.ghepLichSu(List.of(), ghi, BUOI,
                d -> d.equals(GioVN.ngayCua(luc(16, 20))), true).isEmpty());
        assertEquals(1, TrainingService.ghepLichSu(List.of(), ghi, BUOI,
                d -> d.equals(GioVN.ngayCua(luc(17, 9))), true).size());
    }

    @Test
    @DisplayName("Lựa chọn 'Tham gia' không phải ghi chú; buổi đã xoá không làm hỏng danh sách")
    void boQuaThamGiaVaBuoiDaXoa() {
        TrainingRsvp thamGia = ghiChu(4, null, "PENDING");
        thamGia.setChoice("JOIN");
        var ds = TrainingService.ghepLichSu(List.of(diemDanh(99, luc(10, 9), "REAL")), List.of(thamGia),
                BUOI, TrainingLichSuTest::thang11, true);
        assertEquals(1, ds.size());
        assertNull(ds.get(0).getSessionTitle(), "buổi đã xoá: không có tên, app tự hiện 'Buổi đào tạo'");
        assertEquals(99L, ds.get(0).getSessionId());
    }
}
