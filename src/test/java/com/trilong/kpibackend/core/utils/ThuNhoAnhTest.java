package com.trilong.kpibackend.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Nén ảnh vượt trần 10 MB của Cloudinary (gói Free) trước khi gửi. */
public class ThuNhoAnhTest {

    public static byte[] png(BufferedImage anh) throws IOException {
        ByteArrayOutputStream ra = new ByteArrayOutputStream();
        ImageIO.write(anh, "png", ra);
        return ra.toByteArray();
    }

    static BufferedImage doc(byte[] b) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(b));
    }

    public static boolean laJpeg(byte[] b) {
        return (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
    }

    /** Ảnh nhiễu ngẫu nhiên: PNG không nén được, nặng như ảnh chụp màn hình bài đăng nhiều hình. */
    public static BufferedImage nhieu(int w, int h) {
        BufferedImage a = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(7);
        int[] dong = new int[w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                dong[x] = r.nextInt(0x1000000);
            }
            a.setRGB(0, y, w, 1, dong, 0, w);
        }
        return a;
    }

    @Test
    @DisplayName("ảnh chụp màn hình PNG 11 MB (lỗi 'File size too large') → JPEG dưới ngưỡng, giữ tỉ lệ")
    void anhPngQuaTran() throws Exception {
        byte[] goc = png(nhieu(1290, 2796)); // cỡ màn hình iPhone Pro Max
        assertThat(goc.length).isGreaterThan(ThuNhoAnh.TRAN_CLOUDINARY);
        assertThat(ThuNhoAnh.canNen(goc.length)).isTrue();

        byte[] ra = ThuNhoAnh.thuNho(goc);

        assertThat(ra.length).isLessThanOrEqualTo(ThuNhoAnh.NGUONG_NEN);
        assertThat(laJpeg(ra)).isTrue();
        BufferedImage a = doc(ra);
        // Ảnh thật nén xong vẫn giữ 1290 px; ảnh nhiễu ở đây nén kém nên có thể phải thu thêm
        assertThat(a.getWidth()).isBetween(960, 1290);
        assertThat((double) a.getHeight() / a.getWidth()).isCloseTo(2796.0 / 1290, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("ảnh nhỏ hơn ngưỡng thì không cần nén")
    void anhNho() {
        assertThat(ThuNhoAnh.canNen(3_000_000)).isFalse();
        assertThat(ThuNhoAnh.canNen(ThuNhoAnh.NGUONG_NEN)).isFalse();
        assertThat(ThuNhoAnh.canNen(ThuNhoAnh.NGUONG_NEN + 1L)).isTrue();
    }

    @Test
    @DisplayName("ảnh ngang quá 1920 px → thu về 1920, giữ tỉ lệ")
    void ngangToiDa() throws Exception {
        BufferedImage a = doc(ThuNhoAnh.thuNho(png(new BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB))));
        assertThat(a.getWidth()).isEqualTo(1920);
        assertThat(a.getHeight()).isEqualTo(1440);
    }

    @Test
    @DisplayName("ảnh chụp cuộn rất dài → khoảng 12 MP (Cloudinary Free chặn trên 25 MP), vẫn đọc được chữ")
    void anhCuonDai() throws Exception {
        BufferedImage a = doc(ThuNhoAnh.thuNho(png(new BufferedImage(1080, 22000, BufferedImage.TYPE_INT_RGB))));
        assertThat((long) a.getWidth() * a.getHeight()).isLessThanOrEqualTo(12_000_000L + 30_000L);
        assertThat(a.getWidth()).isGreaterThan(700);
        assertThat((double) a.getHeight() / a.getWidth()).isCloseTo(22000.0 / 1080, org.assertj.core.data.Offset.offset(0.2));

        // Dài hơn nữa: đọc thưa để không tràn bộ nhớ máy chủ, vẫn không vượt trần Cloudinary
        BufferedImage b = doc(ThuNhoAnh.thuNho(png(new BufferedImage(1080, 40000, BufferedImage.TYPE_INT_RGB))));
        assertThat((long) b.getWidth() * b.getHeight()).isLessThanOrEqualTo(25_000_000L);
    }

    @Test
    @DisplayName("PNG trong suốt → nền trắng (JPEG không có kênh trong suốt, không thành nền đen)")
    void nenTrongSuot() throws Exception {
        BufferedImage trongSuot = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = trongSuot.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 100); // nửa trái đỏ, nửa phải trong suốt
        g.dispose();

        BufferedImage a = doc(ThuNhoAnh.thuNho(png(trongSuot)));
        Color phai = new Color(a.getRGB(150, 50));
        Color trai = new Color(a.getRGB(50, 50));
        assertThat(phai.getRed() + phai.getGreen() + phai.getBlue()).isGreaterThan(740);
        assertThat(trai.getRed()).isGreaterThan(230);
        assertThat(trai.getGreen()).isLessThan(30);
    }

    @Test
    @DisplayName("JPEG xoay bằng EXIF (ảnh điện thoại dựng đứng) → ảnh ra đứng đúng chiều")
    void xoayTheoExif() throws Exception {
        // Cảm biến lưu ngang 400×200, thẻ Orientation=6 bảo "xoay 90° khi hiển thị".
        // Nửa trái (theo cảm biến) đỏ → hiển thị đúng thì đỏ nằm nửa TRÊN.
        BufferedImage camBien = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = camBien.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 400, 200);
        g.setColor(Color.RED);
        g.fillRect(0, 0, 200, 200);
        g.dispose();
        ByteArrayOutputStream ra = new ByteArrayOutputStream();
        ImageIO.write(camBien, "jpeg", ra);
        byte[] jpeg = chenExif(ra.toByteArray(), 6, false);
        assertThat(ThuNhoAnh.huongExif(jpeg)).isEqualTo(6);
        assertThat(ThuNhoAnh.huongExif(chenExif(ra.toByteArray(), 8, true))).isEqualTo(8);

        BufferedImage a = doc(ThuNhoAnh.thuNho(jpeg));
        assertThat(a.getWidth()).isEqualTo(200);
        assertThat(a.getHeight()).isEqualTo(400);
        assertThat(new Color(a.getRGB(100, 100)).getRed()).isGreaterThan(200);   // nửa trên đỏ
        assertThat(new Color(a.getRGB(100, 300)).getBlue()).isGreaterThan(200);  // nửa dưới xanh
    }

    @Test
    @DisplayName("PNG / ảnh không có EXIF → coi như đứng đúng chiều")
    void khongExif() throws Exception {
        assertThat(ThuNhoAnh.huongExif(png(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)))).isEqualTo(1);
        assertThat(ThuNhoAnh.huongExif(new byte[]{1, 2})).isEqualTo(1);
    }

    @Test
    @DisplayName("định dạng máy chủ không đọc được (HEIC...) → báo người dùng, không phải lỗi hệ thống")
    void dinhDangLa() {
        byte[] rac = new byte[ThuNhoAnh.NGUONG_NEN + 10];
        new Random(1).nextBytes(rac);
        assertThatThrownBy(() -> ThuNhoAnh.thuNho(rac))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 MB");
    }

    /** Chèn đoạn APP1 Exif (chỉ có thẻ Orientation) ngay sau SOI. */
    static byte[] chenExif(byte[] jpeg, int huong, boolean xuoi) {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        if (xuoi) {
            tiff.writeBytes(new byte[]{'I', 'I', 42, 0, 8, 0, 0, 0, 1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0,
                    (byte) huong, 0, 0, 0, 0, 0, 0, 0, 0});
        } else {
            tiff.writeBytes(new byte[]{'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1,
                    0, (byte) huong, 0, 0, 0, 0, 0, 0, 0});
        }
        byte[] t = tiff.toByteArray();
        int dai = 2 + 6 + t.length;
        ByteArrayOutputStream ra = new ByteArrayOutputStream();
        ra.write(0xFF);
        ra.write(0xD8);
        ra.write(0xFF);
        ra.write(0xE1);
        ra.write(dai >> 8);
        ra.write(dai & 0xFF);
        ra.writeBytes(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        ra.writeBytes(t);
        ra.write(jpeg, 2, jpeg.length - 2);
        return ra.toByteArray();
    }
}
