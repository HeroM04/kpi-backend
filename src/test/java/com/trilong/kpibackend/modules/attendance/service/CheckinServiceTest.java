package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.modules.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Giờ chấm công: đúng giờ hay muộn, và khi nào được tính tăng ca.
 *
 * <p>Mỗi ngày cả công ty chấm công qua đây. Lệch một phút ở mốc là cả trăm
 * người bị trừ oan; lệch múi giờ là cả ngày chấm sai.
 */
class CheckinServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private final CheckinService service = new CheckinService();

    private final User binhThuong = User.builder().id(1L).allowCheckinUntil9(false).build();
    private final User chamChuoc9h = User.builder().id(2L).allowCheckinUntil9(true).build();

    private ZonedDateTime luc(int gio, int phut, int giay) {
        return ZonedDateTime.of(2026, 9, 23, gio, phut, giay, 0, VN); // thứ Tư
    }

    @Test
    @DisplayName("Ca sáng: tới 08:45:00 là đúng giờ, 08:45:01 là muộn")
    void mocCaSang() {
        assertThat(service.laDungGio(binhThuong, luc(8, 30, 0))).isTrue();
        assertThat(service.laDungGio(binhThuong, luc(8, 45, 0))).isTrue();
        assertThat(service.laDungGio(binhThuong, luc(8, 45, 1))).isFalse();
        assertThat(service.laDungGio(binhThuong, luc(11, 59, 0))).isFalse();
    }

    @Test
    @DisplayName("Người được châm chước: tới 09:00 vẫn đúng giờ")
    void chamChuoc() {
        assertThat(service.laDungGio(chamChuoc9h, luc(8, 59, 0))).isTrue();
        assertThat(service.laDungGio(chamChuoc9h, luc(9, 0, 0))).isTrue();
        assertThat(service.laDungGio(chamChuoc9h, luc(9, 0, 1))).isFalse();
    }

    @Test
    @DisplayName("Ca chiều: 12:00–13:45 đúng giờ, sau 13:45 muộn — kể cả người được châm chước")
    void caChieu() {
        assertThat(service.laDungGio(binhThuong, luc(12, 0, 0))).isTrue();
        assertThat(service.laDungGio(binhThuong, luc(13, 45, 0))).isTrue();
        assertThat(service.laDungGio(binhThuong, luc(13, 46, 0))).isFalse();
        assertThat(service.laDungGio(chamChuoc9h, luc(13, 46, 0))).isFalse();
    }

    @Test
    @DisplayName("Giờ đọc từ DB mang múi UTC vẫn so theo giờ VN (08:30 VN = 01:30 UTC)")
    void quyVeGioVN() {
        ZonedDateTime utc = luc(8, 30, 0).withZoneSameInstant(ZoneOffset.UTC);
        assertThat(utc.getHour()).isEqualTo(1);
        assertThat(service.laDungGio(binhThuong, utc)).isTrue();

        ZonedDateTime muonUtc = luc(9, 30, 0).withZoneSameInstant(ZoneOffset.UTC); // 02:30 UTC
        assertThat(service.laDungGio(binhThuong, muonUtc)).isFalse();
    }

    @Test
    @DisplayName("Tăng ca: check-out từ 20:00 giờ VN ngày thường")
    void tangCa() {
        assertThat(CheckinService.duDieuKienTangCa("CHECK_OUT", luc(20, 0, 0))).isTrue();
        assertThat(CheckinService.duDieuKienTangCa("CHECK_OUT", luc(22, 17, 0))).isTrue();
        assertThat(CheckinService.duDieuKienTangCa("CHECK_OUT", luc(19, 59, 59))).isFalse();
    }

    @Test
    @DisplayName("Tăng ca: giờ UTC từ DB vẫn nhận đúng (20:30 VN = 13:30 UTC)")
    void tangCaTuGioUtc() {
        ZonedDateTime utc = luc(20, 30, 0).withZoneSameInstant(ZoneOffset.UTC);
        assertThat(CheckinService.duDieuKienTangCa("CHECK_OUT", utc)).isTrue();
    }

    @Test
    @DisplayName("Không tính tăng ca: check-in, thứ Bảy, Chủ nhật")
    void khongTangCa() {
        assertThat(CheckinService.duDieuKienTangCa("CHECK_IN", luc(21, 0, 0))).isFalse();
        ZonedDateTime thuBay = ZonedDateTime.of(2026, 9, 26, 21, 0, 0, 0, VN);
        ZonedDateTime chuNhat = ZonedDateTime.of(2026, 9, 27, 21, 0, 0, 0, VN);
        assertThat(CheckinService.duDieuKienTangCa("CHECK_OUT", thuBay)).isFalse();
        assertThat(CheckinService.duDieuKienTangCa("CHECK_OUT", chuNhat)).isFalse();
        assertThat(CheckinService.duDieuKienTangCa("CHECK_OUT", null)).isFalse();
    }
}
