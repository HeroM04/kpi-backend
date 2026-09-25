package com.trilong.kpibackend.core.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ngày theo giờ Việt Nam — chạy dưới múi giờ máy UTC giống hệt máy chủ Render.
 *
 * <p>Trên máy dev (giờ VN) lỗi lệch ngày không bao giờ lộ ra, vì giờ máy trùng
 * giờ VN. Nên test tự đặt múi giờ máy về UTC.
 */
class GioVNTest {

    private TimeZone goc;

    @BeforeEach
    void datMuiGioMayChu() {
        goc = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach
    void traLai() {
        TimeZone.setDefault(goc);
    }

    @Test
    @DisplayName("Bài đăng lúc 06:00 sáng 23/09 giờ VN (22/09 ở UTC) thuộc ngày 23/09")
    void sangSomThuocDungNgay() {
        ZonedDateTime tuDb = ZonedDateTime.of(2026, 9, 23, 6, 0, 0, 0, GioVN.VN).withZoneSameInstant(ZoneOffset.UTC);
        assertThat(tuDb.toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 22)); // cách cũ: sai
        assertThat(GioVN.ngayCua(tuDb)).isEqualTo(LocalDate.of(2026, 9, 23));
    }

    @Test
    @DisplayName("Mốc không múi giờ (ghi theo giờ máy UTC) cũng quy đúng về ngày VN")
    void mocKhongMuiGio() {
        // Phản hồi gửi lúc 05:30 sáng 24/09 giờ VN, @CreationTimestamp ghi giờ máy UTC: 23/09 22:30
        LocalDateTime ghiTheoGioMay = LocalDateTime.of(2026, 9, 23, 22, 30);
        assertThat(GioVN.ngayCua(ghiTheoGioMay)).isEqualTo(LocalDate.of(2026, 9, 24));
    }

    @Test
    @DisplayName("Tối muộn giờ VN vẫn đúng ngày (không lệch sang hôm sau)")
    void toiMuon() {
        ZonedDateTime tuDb = ZonedDateTime.of(2026, 9, 23, 23, 30, 0, 0, GioVN.VN).withZoneSameInstant(ZoneOffset.UTC);
        assertThat(GioVN.ngayCua(tuDb)).isEqualTo(LocalDate.of(2026, 9, 23));
    }

    @Test
    @DisplayName("?date bỏ trống → hôm nay theo giờ VN; có giá trị → đúng ngày đó")
    void thamSoNgay() {
        assertThat(GioVN.ngayLoc(null)).isEqualTo(LocalDate.now(GioVN.VN));
        assertThat(GioVN.ngayLoc("  ")).isEqualTo(LocalDate.now(GioVN.VN));
        assertThat(GioVN.ngayLoc("2026-09-23")).isEqualTo(LocalDate.of(2026, 9, 23));
    }

    @Test
    @DisplayName("Giá trị rỗng không ném lỗi")
    void giaTriRong() {
        assertThat(GioVN.ngayCua((ZonedDateTime) null)).isNull();
        assertThat(GioVN.ngayCua((LocalDateTime) null)).isNull();
    }
}
