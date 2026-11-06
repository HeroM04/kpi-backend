package com.trilong.kpibackend.core.utils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Nén ảnh vượt trần của Cloudinary trước khi gửi lên.
 *
 * <p>Cloudinary gói Free từ chối ảnh trên 10 MB ("File size too large") và trên
 * 25 MP. Ảnh chụp màn hình trên iPhone/Android là PNG, image_picker trong app
 * không nén được PNG (imageQuality chỉ áp dụng cho JPEG), nên một ảnh chụp
 * màn hình bài đăng nhiều hình có thể nặng 11 MB và bị từ chối.
 *
 * <p>Ảnh vượt ngưỡng được đổi sang JPEG, ngang tối đa 1920 px (đúng cỡ Cloudinary
 * vẫn cắt khi lưu, nên không mất gì so với trước), giữ chiều xoay theo EXIF.
 * Ảnh nhỏ hơn ngưỡng không đụng tới.
 */
public final class ThuNhoAnh {

    /** Trần dung lượng một ảnh của Cloudinary gói Free (10 MB). */
    public static final int TRAN_CLOUDINARY = 10 * 1024 * 1024;
    /** Ảnh nặng hơn mức này thì nén lại; chừa khoảng an toàn dưới trần. */
    public static final int NGUONG_NEN = 9_500_000;

    static final int NGANG_TOI_DA = 1920;
    /** Dưới trần 25 MP của Cloudinary; ảnh chụp cuộn 1080×22000 ra cỡ 768 px ngang, vẫn đọc được chữ. */
    static final long DIEM_ANH_TOI_DA = 12_000_000L;
    /** Giải mã thưa (bỏ bớt điểm ảnh) để ảnh rất lớn không ngốn hết bộ nhớ máy chủ (heap ~300 MB). */
    static final long DIEM_ANH_GIAI_MA_TOI_DA = 24_000_000L;

    static final String KHONG_NEN_DUOC =
            "Ảnh nặng hơn 10 MB và máy chủ không tự nén được định dạng này. Chụp màn hình lại hoặc chọn ảnh khác nhỏ hơn.";

    /** Mỗi lúc chỉ nén một ảnh lớn, để hai ảnh 20 MB cùng lúc không làm tràn bộ nhớ. */
    private static final Object KHOA = new Object();

    private ThuNhoAnh() {
    }

    public static boolean canNen(long soByte) {
        return soByte > NGUONG_NEN;
    }

    /**
     * Đổi ảnh sang JPEG nhỏ hơn {@link #NGUONG_NEN}.
     *
     * @throws IllegalArgumentException định dạng không đọc được (HEIC, WebP...) hoặc nén mãi vẫn quá to
     */
    public static byte[] thuNho(byte[] goc) throws IOException {
        synchronized (KHOA) {
            int huong = huongExif(goc);
            BufferedImage anh = giaiMa(goc, huong);
            boolean nam = huong >= 5; // 5–8: ảnh xoay 90°, đổi ngang ↔ dọc
            int rong = nam ? anh.getHeight() : anh.getWidth();
            int cao = nam ? anh.getWidth() : anh.getHeight();
            double tiLe = tiLe(rong, cao);
            BufferedImage ra = ve(anh, huong, tiLe);
            anh = null; // nhả ảnh giải mã trước khi mã hóa

            for (int lan = 0; lan < 4; lan++) {
                for (float chatLuong : new float[]{0.85f, 0.75f, 0.65f}) {
                    byte[] jpeg = maHoaJpeg(ra, chatLuong);
                    if (jpeg.length <= NGUONG_NEN) {
                        return jpeg;
                    }
                }
                ra = ve(ra, 1, 0.75);
            }
            throw new IllegalArgumentException(KHONG_NEN_DUOC);
        }
    }

    /** Tỉ lệ thu (≤ 1) để ngang ≤ 1920 px và tổng điểm ảnh ≤ 12 MP. */
    static double tiLe(int rong, int cao) {
        double k = Math.min(1.0, (double) NGANG_TOI_DA / rong);
        return Math.min(k, Math.sqrt((double) DIEM_ANH_TOI_DA / ((double) rong * cao)));
    }

    private static BufferedImage giaiMa(byte[] goc, int huong) throws IOException {
        try (ImageInputStream vao = new MemoryCacheImageInputStream(new ByteArrayInputStream(goc))) {
            Iterator<ImageReader> ds = ImageIO.getImageReaders(vao);
            if (!ds.hasNext()) {
                throw new IllegalArgumentException(KHONG_NEN_DUOC);
            }
            ImageReader doc = ds.next();
            try {
                doc.setInput(vao, true, true);
                int w = doc.getWidth(0);
                int h = doc.getHeight(0);
                double k = huong >= 5 ? tiLe(h, w) : tiLe(w, h);
                // Đọc thưa: lấy 1 điểm trong mỗi "buoc" điểm. Vẫn giữ ảnh giải mã ≥ cỡ đích.
                int buoc = (int) Math.max(1, Math.floor(1 / k));
                buoc = (int) Math.max(buoc, Math.ceil(Math.sqrt((double) w * h / DIEM_ANH_GIAI_MA_TOI_DA)));
                ImageReadParam p = doc.getDefaultReadParam();
                p.setSourceSubsampling(buoc, buoc, 0, 0);
                return doc.read(0, p);
            } catch (IOException | RuntimeException e) {
                // JPEG CMYK, PNG hỏng... → báo người dùng, không phải "lỗi hệ thống"
                throw new IllegalArgumentException(KHONG_NEN_DUOC, e);
            } finally {
                doc.dispose();
            }
        }
    }

    /** Vẽ sang ảnh RGB (nền trắng thay cho phần trong suốt), xoay theo EXIF rồi thu theo tỉ lệ. */
    private static BufferedImage ve(BufferedImage anh, int huong, double tiLe) {
        int w = anh.getWidth();
        int h = anh.getHeight();
        boolean nam = huong >= 5;
        int rong = Math.max(1, (int) Math.round((nam ? h : w) * tiLe));
        int cao = Math.max(1, (int) Math.round((nam ? w : h) * tiLe));
        BufferedImage ra = new BufferedImage(rong, cao, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = ra.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rong, cao);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.scale((double) rong / (nam ? h : w), (double) cao / (nam ? w : h));
            g.transform(bienDoiExif(huong, w, h));
            g.drawImage(anh, 0, 0, null);
        } finally {
            g.dispose();
        }
        return ra;
    }

    /** Phép biến đổi đưa ảnh gốc (w×h) về đúng chiều theo thẻ Orientation của EXIF. */
    static AffineTransform bienDoiExif(int huong, int w, int h) {
        return switch (huong) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // lật ngang
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // xoay 180°
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // lật dọc
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // chuyển vị
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // xoay 90° theo chiều kim đồng hồ
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);   // xoay 90° ngược chiều kim đồng hồ
            default -> new AffineTransform();
        };
    }

    private static byte[] maHoaJpeg(BufferedImage anh, float chatLuong) throws IOException {
        ImageWriter ghi = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream ra = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(ra)) {
            ghi.setOutput(out);
            ImageWriteParam p = ghi.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(chatLuong);
            ghi.write(null, new IIOImage(anh, null, null), p);
        } finally {
            ghi.dispose();
        }
        return ra.toByteArray();
    }

    /** Thẻ Orientation (1–8) trong EXIF của JPEG; không phải JPEG hoặc không có thẻ thì 1. */
    static int huongExif(byte[] b) {
        if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int i = 2;
        while (i + 4 <= b.length) {
            if ((b[i] & 0xFF) != 0xFF) {
                return 1;
            }
            int loai = b[i + 1] & 0xFF;
            if (loai == 0xDA || loai == 0xD9) {
                return 1; // tới phần dữ liệu ảnh mà chưa thấy EXIF
            }
            int dai = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
            int het = i + 2 + dai;
            if (loai == 0xE1 && dai >= 16 && het <= b.length
                    && b[i + 4] == 'E' && b[i + 5] == 'x' && b[i + 6] == 'i' && b[i + 7] == 'f'
                    && b[i + 8] == 0 && b[i + 9] == 0) {
                return huongTrongTiff(b, i + 10, het);
            }
            i = het;
        }
        return 1;
    }

    private static int huongTrongTiff(byte[] b, int goc, int het) {
        if (goc + 8 > het) {
            return 1;
        }
        boolean xuoi = b[goc] == 'I'; // "II" = little-endian, "MM" = big-endian
        long ifd = doc32(b, goc + 4, xuoi);
        if (ifd < 8 || goc + ifd + 2 > het) {
            return 1;
        }
        int p = (int) (goc + ifd);
        int so = doc16(b, p, xuoi);
        for (int k = 0; k < so; k++) {
            int e = p + 2 + k * 12;
            if (e + 12 > het) {
                return 1;
            }
            if (doc16(b, e, xuoi) == 0x0112) {
                int v = doc16(b, e + 8, xuoi);
                return v >= 1 && v <= 8 ? v : 1;
            }
        }
        return 1;
    }

    private static int doc16(byte[] b, int i, boolean xuoi) {
        int a = b[i] & 0xFF;
        int c = b[i + 1] & 0xFF;
        return xuoi ? (c << 8) | a : (a << 8) | c;
    }

    private static long doc32(byte[] b, int i, boolean xuoi) {
        long v = 0;
        for (int k = 0; k < 4; k++) {
            int byteThu = xuoi ? 3 - k : k;
            v = (v << 8) | (b[i + byteThu] & 0xFF);
        }
        return v;
    }
}
