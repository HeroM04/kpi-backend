package com.trilong.kpibackend.core.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Ảnh riêng tư trên S3: chỉ nhận ảnh thật, khóa không lộ người gửi. */
class AnhRiengTuServiceTest {

    private static byte[] dau(int... b) {
        byte[] out = new byte[16];
        for (int i = 0; i < b.length; i++) out[i] = (byte) b[i];
        return out;
    }

    @Test
    @DisplayName("Nhận diện JPG, PNG, WebP theo nội dung file")
    void nhanDienAnh() {
        assertThat(AnhRiengTuService.nhanDien(dau(0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo(AnhRiengTuService.LoaiAnh.JPG);
        assertThat(AnhRiengTuService.nhanDien(dau(0x89, 'P', 'N', 'G', 0x0D, 0x0A))).isEqualTo(AnhRiengTuService.LoaiAnh.PNG);
        assertThat(AnhRiengTuService.nhanDien(dau('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'))).isEqualTo(AnhRiengTuService.LoaiAnh.WEBP);
    }

    @Test
    @DisplayName("File đổi đuôi thành .jpg nhưng bên trong là HTML/PDF → không nhận")
    void khongNhanFileGiaAnh() {
        assertThat(AnhRiengTuService.nhanDien("<html><script>alert(1)</script>".getBytes())).isNull();
        assertThat(AnhRiengTuService.nhanDien("%PDF-1.7 ...........".getBytes())).isNull();
        assertThat(AnhRiengTuService.nhanDien(new byte[3])).isNull();
        assertThat(AnhRiengTuService.nhanDien(null)).isNull();
    }

    @Test
    @DisplayName("Khóa ảnh theo thư mục/năm/tháng, không chứa mã người gửi (góp ý ẩn danh)")
    void khoaKhongLoNguoiGui() {
        String khoa = AnhRiengTuService.taoKhoa("feedback", LocalDate.of(2026, 9, 29), "abc-123", "jpg");
        assertThat(khoa).isEqualTo("feedback/2026/09/abc-123.jpg");
    }

    @SuppressWarnings("unchecked")
    private AnhRiengTuService khongCoS3() {
        return new AnhRiengTuService(mock(ObjectProvider.class), mock(ObjectProvider.class));
    }

    @Test
    @DisplayName("Máy chủ chưa cấu hình S3: báo rõ, không ném lỗi lạ; link xem trả null")
    void chuaCauHinhS3() {
        AnhRiengTuService s = khongCoS3();
        assertThat(s.sanSang()).isFalse();
        var anh = new MockMultipartFile("images", "a.jpg", "image/jpeg", dau(0xFF, 0xD8, 0xFF));
        assertThatThrownBy(() -> s.taiLen(anh, "feedback"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("S3");
        assertThat(s.linkXem("feedback/2026/09/x.jpg")).isNull();
        s.xoa("feedback/2026/09/x.jpg");   // không ném
    }
}
