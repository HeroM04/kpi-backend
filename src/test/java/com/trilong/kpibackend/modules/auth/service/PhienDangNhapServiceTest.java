package com.trilong.kpibackend.modules.auth.service;

import com.trilong.kpibackend.core.security.HoSoNongService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hai chỗ dễ sai lặng lẽ của tính năng quản lý phiên đăng nhập:
 * đọc tên thiết bị từ User-Agent, và mốc chặn token cũ.
 *
 * <p>Mốc chặn sai theo hướng "lỏng" thì bấm đăng xuất mọi thiết bị xong người
 * kia vẫn dùng được cả tiếng; sai theo hướng "chặt" thì chính người vừa đăng
 * nhập cũng bị đá ra ngay. Không có kiểm thử thì cả hai đều chỉ lộ ra khi đã
 * lên máy thật.
 */
class PhienDangNhapServiceTest {

    @Test
    @DisplayName("Đọc tên thiết bị từ User-Agent")
    void docTenThietBi() {
        assertEquals("Chrome trên Windows", PhienDangNhapService.moTaThietBi(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36"));

        // Edge cũng chứa chữ "Chrome" và "Safari" — phải nhận ra Edge trước
        assertEquals("Microsoft Edge trên Windows", PhienDangNhapService.moTaThietBi(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36 Edg/140.0"));

        assertEquals("Safari trên iPhone/iPad", PhienDangNhapService.moTaThietBi(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1"));

        assertEquals("App Trí Long (Android)", PhienDangNhapService.moTaThietBi("Dart/3.5 (dart:io) Android"));
        assertEquals("Không rõ thiết bị", PhienDangNhapService.moTaThietBi(null));
        assertEquals("Không rõ thiết bị", PhienDangNhapService.moTaThietBi("   "));
    }

    /** Bản chụp nhân sự với mốc "đăng xuất mọi thiết bị" và danh sách phiên cho trước. */
    private HoSoNongService.BanChup banChup(ZonedDateTime phienHopLeTu, Set<Long> phien) {
        return new HoSoNongService.BanChup("ADMIN", 1L, "ACTIVE", "Nguyễn Mạnh Hùng", null,
                phienHopLeTu, phien, System.currentTimeMillis() + 60_000);
    }

    @Test
    @DisplayName("Token phát trước lúc đăng xuất toàn bộ thì bị chặn, phát sau thì vẫn dùng")
    void chanTokenCu() {
        ZonedDateTime dangXuatLuc = ZonedDateTime.now();
        var b = banChup(dangXuatLuc, Set.of(1L));

        Date truocDo = Date.from(dangXuatLuc.minusMinutes(5).toInstant());
        Date sauDo   = Date.from(dangXuatLuc.plusMinutes(1).toInstant());

        assertTrue(b.tokenQuaCu(truocDo), "token cũ phải bị chặn");
        assertFalse(b.tokenQuaCu(sauDo), "token cấp sau khi đăng xuất phải dùng được");
    }

    @Test
    @DisplayName("Chưa từng đăng xuất toàn bộ thì không chặn token nào")
    void chuaTungDangXuatThiKhongChan() {
        var b = banChup(null, Set.of(1L));
        assertFalse(b.tokenQuaCu(new Date(0)));
        assertFalse(b.tokenQuaCu(null));
    }

    @Test
    @DisplayName("Phiên bị gỡ thì token của riêng máy đó hết hiệu lực")
    void chanPhienDaThuHoi() {
        var b = banChup(null, Set.of(7L, 9L));
        assertFalse(b.phienBiThuHoi(7L), "phiên còn trong danh sách thì vẫn chạy");
        assertTrue(b.phienBiThuHoi(8L), "phiên đã gỡ phải bị chặn");
        // Token đời cũ không mang sid — không được chặn, nếu không cả công ty bị
        // đá ra ngay lúc máy chủ lên bản mới.
        assertFalse(b.phienBiThuHoi(null));
    }
}
