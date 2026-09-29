package com.trilong.kpibackend.core.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ảnh riêng tư trên S3 — hiện dùng cho ảnh đính kèm góp ý nhân sự.
 *
 * <p>Ảnh chụp màn hình báo lỗi hay khiếu nại có thể lộ điểm số, tin nhắn, số
 * điện thoại. Nên ảnh để RIÊNG TƯ trong bucket (không cần mở công khai), DB
 * chỉ lưu khóa của ảnh; ai được xem góp ý thì nhận một link ký tạm hết hạn
 * sau {@link #HAN_LINK}. Lộ link cũng chỉ xem được trong thời hạn đó.
 *
 * <p>Khóa không chứa mã người gửi, để góp ý ẩn danh không bị lộ qua đường dẫn
 * ảnh.
 *
 * <p>Máy chủ chạy không có S3 (profile dev/test lưu ảnh ở máy) thì dịch vụ vẫn
 * khởi động, chỉ báo {@link #sanSang()} = false.
 */
@Slf4j
@Service
public class AnhRiengTuService {

    /** Tối đa mỗi ảnh — app đã thu nhỏ trước khi gửi nên thường chỉ vài trăm KB. */
    public static final long TOI_DA_BYTE = 10L * 1024 * 1024;

    /** Link xem ảnh sống bao lâu. App và web tải lại danh sách là có link mới. */
    public static final Duration HAN_LINK = Duration.ofHours(24);

    private final S3Client s3;
    private final S3Presigner presigner;

    @Value("${aws.s3.bucket-name:}")
    private String bucket;

    public AnhRiengTuService(ObjectProvider<S3Client> s3, ObjectProvider<S3Presigner> presigner) {
        this.s3 = s3.getIfAvailable();
        this.presigner = presigner.getIfAvailable();
    }

    public boolean sanSang() {
        return s3 != null && presigner != null && bucket != null && !bucket.isBlank();
    }

    /**
     * Đưa một ảnh lên S3, trả về khóa để lưu vào DB.
     *
     * @throws IllegalArgumentException file trống, quá lớn, hoặc không phải ảnh JPG/PNG/WebP
     * @throws IllegalStateException    máy chủ chưa cấu hình S3
     */
    public String taiLen(MultipartFile file, String thuMuc) throws IOException {
        if (!sanSang()) {
            throw new IllegalStateException("Máy chủ chưa cấu hình kho ảnh S3, tạm thời chưa gửi được ảnh.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Ảnh trống.");
        }
        if (file.getSize() > TOI_DA_BYTE) {
            throw new IllegalArgumentException("Ảnh quá lớn (tối đa 10 MB mỗi ảnh).");
        }
        byte[] du = file.getBytes();
        LoaiAnh loai = nhanDien(du);
        if (loai == null) {
            throw new IllegalArgumentException("Chỉ nhận ảnh JPG, PNG hoặc WebP.");
        }

        String khoa = taoKhoa(thuMuc, LocalDate.now(com.trilong.kpibackend.core.utils.GioVN.VN),
                UUID.randomUUID().toString(), loai.duoi);
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(khoa)
                        // Loại lấy theo nội dung file, không tin loại do app khai
                        .contentType(loai.mime)
                        .contentLength((long) du.length)
                        .build(),
                RequestBody.fromBytes(du));
        return khoa;
    }

    /** Link xem có hạn; không ký được (S3 chưa cấu hình, khóa hỏng) thì null. */
    public String linkXem(String khoa) {
        if (!sanSang() || khoa == null || khoa.isBlank()) return null;
        try {
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(HAN_LINK)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(khoa).build())
                    .build()).url().toString();
        } catch (Exception e) {
            log.warn("[S3] Không ký được link ảnh {}: {}", khoa, e.getMessage());
            return null;
        }
    }

    /** Xóa ảnh, lỗi thì bỏ qua — còn sót một ảnh riêng tư không hại gì. */
    public void xoa(String khoa) {
        if (!sanSang() || khoa == null || khoa.isBlank()) return;
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(khoa).build());
        } catch (Exception e) {
            log.warn("[S3] Không xóa được ảnh {}: {}", khoa, e.getMessage());
        }
    }

    // ── Phần thuần, test được không cần AWS ──────────────────────────────────

    enum LoaiAnh {
        JPG("jpg", "image/jpeg"), PNG("png", "image/png"), WEBP("webp", "image/webp");
        final String duoi, mime;
        LoaiAnh(String duoi, String mime) { this.duoi = duoi; this.mime = mime; }
    }

    /** Nhận diện ảnh theo mấy byte đầu file; không phải JPG/PNG/WebP thì null. */
    static LoaiAnh nhanDien(byte[] b) {
        if (b == null || b.length < 12) return null;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return LoaiAnh.JPG;
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return LoaiAnh.PNG;
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return LoaiAnh.WEBP;
        return null;
    }

    /** {@code feedback/2026/09/<uuid>.jpg} — không có mã người gửi. */
    static String taoKhoa(String thuMuc, LocalDate ngay, String uuid, String duoi) {
        return String.format("%s/%d/%02d/%s.%s", thuMuc, ngay.getYear(), ngay.getMonthValue(), uuid, duoi);
    }
}
