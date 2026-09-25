package com.trilong.kpibackend.modules.training.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mã QR điểm danh: bao lâu thì còn được nhận.
 *
 * <p>Hẹp quá là học viên quét đúng mã đang chiếu vẫn bị báo hết hạn (buổi
 * 25/09/2026, lớp 20/200 mà nhiều người không điểm danh được); rộng quá thì
 * ảnh chụp mã gửi qua Zalo cho người vắng mặt vẫn dùng được.
 */
class MaQrDiemDanhTest {

    /** Đầu một vòng 30 giây, để dễ nói "giây thứ mấy của vòng". */
    private static final long DAU_VONG = 1_790_000_010_000L / 30_000L * 30_000L;

    /** Token web/app sinh ra tại thời điểm {@code ms} với vòng {@code vong} — cùng công thức ManageTraining.jsx. */
    private static String ma(long ms, long vong) {
        return String.format("%06d", ((ms / vong) * 31337L) % 999999L);
    }

    private static String ma30(long ms) { return ma(ms, 30_000L); }

    @Test
    @DisplayName("Mã 30 giây còn được nhận tới hết vòng kế tiếp (30–60 giây sau khi hiện)")
    void maConHanMotVongSau() {
        String dangChieu = ma30(DAU_VONG + 1_000);
        assertTrue(TrainingService.tokenHopLe(dangChieu, DAU_VONG + 1_000), "vừa hiện");
        assertTrue(TrainingService.tokenHopLe(dangChieu, DAU_VONG + 29_000), "cuối vòng");
        assertTrue(TrainingService.tokenHopLe(dangChieu, DAU_VONG + 59_000), "cuối vòng kế tiếp");
        assertFalse(TrainingService.tokenHopLe(dangChieu, DAU_VONG + 61_000), "sang vòng thứ ba thì hết hạn");
    }

    @Test
    @DisplayName("Quét ngay trước lúc đổi mã, yêu cầu tới máy chủ khi mã đã đổi — vẫn nhận")
    void quetSatLucDoiMa() {
        String dangChieu = ma30(DAU_VONG + 29_500);
        assertTrue(TrainingService.tokenHopLe(dangChieu, DAU_VONG + 31_000));
    }

    @Test
    @DisplayName("Đồng hồ máy chiếu nhanh hơn máy chủ vài giây — vẫn nhận")
    void dongHoMayChieuNhanHon() {
        String dangChieu = ma30(DAU_VONG + 30_500);   // máy chiếu đã sang vòng mới
        assertTrue(TrainingService.tokenHopLe(dangChieu, DAU_VONG + 28_000));
    }

    @Test
    @DisplayName("Mã vòng 10 giây của app bản cũ vẫn được nhận")
    void maMuoiGiayCuaBanCu() {
        long t = DAU_VONG + 12_000;
        assertTrue(TrainingService.tokenHopLe(ma(t, 10_000L), t + 5_000));
    }

    @Test
    @DisplayName("Đối chiếu theo lúc máy chủ NHẬN: xếp hàng chờ DB bao lâu mã cũng không hết hạn")
    void xepHangChoDbKhongLamHetHan() {
        String dangChieu = ma30(DAU_VONG + 2_000);
        long lucNhan = DAU_VONG + 3_000;
        long lucXuLyXong = DAU_VONG + 75_000;   // chờ kết nối DB rất lâu

        assertFalse(TrainingService.tokenHopLe(dangChieu, lucXuLyXong),
                "đối chiếu theo lúc xử lý (cách cũ) thì học viên bị báo hết hạn oan");
        assertTrue(TrainingService.tokenHopLe(dangChieu, lucNhan));
    }

    @Test
    @DisplayName("Chuỗi linh tinh không phải mã")
    void chuoiLinhTinh() {
        assertFalse(TrainingService.tokenHopLe("abcdef", DAU_VONG));
        assertFalse(TrainingService.tokenHopLe("12345", DAU_VONG));
    }
}
