package com.trilong.kpibackend.modules.attendance.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chấm công gửi chậm do mạng (18/11/2026: nhiều người bấm sát 08:45, mạng kẹt,
 * vài phút sau mới gửi được). Giờ chấm công phải là giờ BẤM, không phải giờ
 * máy chủ nhận — và không được để chỉnh giờ điện thoại mà "đi sớm" được.
 */
class GuiChamDoMangTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private static ZonedDateTime luc(int gio, int phut, int giay) {
        return ZonedDateTime.of(2026, 11, 18, gio, phut, giay, 0, VN);
    }

    @Test
    @DisplayName("App cũ (không gửi gì thêm) → giờ máy chủ như trước")
    void appCu() {
        var g = CheckinService.xetGioBam(luc(8, 47, 0), null, null);
        assertEquals(luc(8, 47, 0), g.gio());
        assertNull(g.guiChamGiay());
        assertFalse(g.canDuyet());
    }

    @Test
    @DisplayName("Bấm 08:44, mạng kẹt, 08:47 mới tới → tính 08:44, tự nhận, ghi nhãn gửi chậm")
    void guiCham3Phut() {
        var g = CheckinService.xetGioBam(luc(8, 47, 10), luc(8, 44, 10), 180L);
        assertEquals(luc(8, 44, 10), g.gio());
        assertEquals(180, g.guiChamGiay());
        assertFalse(g.canDuyet());
    }

    @Test
    @DisplayName("Tải ảnh mất 20 giây → giờ bấm, không gắn nhãn gửi chậm")
    void guiNgay() {
        var g = CheckinService.xetGioBam(luc(8, 30, 20), luc(8, 30, 0), 20L);
        assertEquals(luc(8, 30, 0), g.gio());
        assertNull(g.guiChamGiay());
        assertFalse(g.canDuyet());
    }

    @Test
    @DisplayName("Đúng 30 phút vẫn tự nhận; quá 30 phút thì chờ duyệt, vẫn giữ giờ bấm")
    void moc30Phut() {
        assertFalse(CheckinService.xetGioBam(luc(9, 14, 0), null, 1800L).canDuyet());
        var qua = CheckinService.xetGioBam(luc(9, 15, 0), null, 1801L);
        assertTrue(qua.canDuyet());
        assertEquals(luc(8, 44, 59), qua.gio());
        assertTrue(qua.lyDo().contains("08:44"), qua.lyDo());
    }

    @Test
    @DisplayName("Chỉnh đồng hồ điện thoại lùi về 08:25 cũng vô ích: giờ bấm tính bằng giờ máy chủ trừ thời gian chờ")
    void chinhGioDienThoai() {
        // Thực tế bấm 09:00, điện thoại để 08:25, gửi ngay (chờ 5 giây)
        var g = CheckinService.xetGioBam(luc(9, 0, 5), luc(8, 25, 0), 5L);
        assertEquals(luc(9, 0, 0), g.gio());
    }

    @Test
    @DisplayName("Máy khởi động lại (không đo được thời gian chờ) → lấy giờ điện thoại nhưng bắt chờ duyệt")
    void khoiDongLai() {
        var g = CheckinService.xetGioBam(luc(9, 10, 0), luc(8, 44, 0), null);
        assertEquals(luc(8, 44, 0), g.gio());
        assertTrue(g.canDuyet());
        assertTrue(g.lyDo().contains("khởi động lại"), g.lyDo());
    }

    @Test
    @DisplayName("Không đo được thời gian chờ nhưng giờ điện thoại gần giờ máy chủ → coi như gửi ngay")
    void khongDoDuocNhungGuiNgay() {
        var g = CheckinService.xetGioBam(luc(8, 30, 0), luc(8, 29, 30), null);
        assertEquals(luc(8, 30, 0), g.gio());
        assertFalse(g.canDuyet());
    }

    @Test
    @DisplayName("Đồng hồ điện thoại chạy nhanh / thời gian chờ âm → bỏ qua, dùng giờ máy chủ")
    void soLieuVoLy() {
        assertEquals(luc(8, 30, 0), CheckinService.xetGioBam(luc(8, 30, 0), luc(9, 30, 0), null).gio());
        assertEquals(luc(8, 30, 0), CheckinService.xetGioBam(luc(8, 30, 0), null, -50L).gio());
    }

    @Test
    @DisplayName("Lượt bấm từ hôm qua gửi hôm nay → ra giờ hôm qua (nơi gọi từ chối), KHÔNG thành lượt hôm nay")
    void luotHomQua() {
        var doDuoc = CheckinService.xetGioBam(luc(8, 30, 0), null, 20L * 3600);
        assertEquals(luc(8, 30, 0).minusHours(20), doDuoc.gio());
        var khongDoDuoc = CheckinService.xetGioBam(luc(8, 30, 0), luc(8, 30, 0).minusHours(30), null);
        assertEquals(luc(8, 30, 0).minusHours(30), khongDoDuoc.gio());
        assertNotEquals(luc(8, 30, 0).toLocalDate(), khongDoDuoc.gio().toLocalDate());
    }
}
