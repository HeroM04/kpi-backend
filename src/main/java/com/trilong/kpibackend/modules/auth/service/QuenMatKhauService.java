package com.trilong.kpibackend.modules.auth.service;

import com.trilong.kpibackend.modules.auth.entity.KhoaKhoiPhuc;
import com.trilong.kpibackend.modules.auth.repository.KhoaKhoiPhucRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Nhân sự quên mật khẩu tự đặt lại về {@value #MAT_KHAU_MAC_DINH} ngay trên app.
 *
 * <p>Điều kiện: làm trên chiếc điện thoại đã từng đăng nhập đúng tài khoản đó
 * (xem {@link KhoaKhoiPhuc}). Máy khác — kể cả biết số điện thoại — chỉ nhận về
 * một câu trả lời chung, không đổi được gì, cũng không dò được số nào có tài
 * khoản.
 *
 * <p>Đặt lại xong thì mọi thiết bị đang đăng nhập bị đá ra như khi đổi mật khẩu.
 * Đăng nhập bằng mật khẩu mặc định thì app nhắc đổi ngay.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuenMatKhauService {

    public static final String MAT_KHAU_MAC_DINH = "123456";

    /** Một người giữ tối đa chừng này khóa (mỗi lần đăng nhập app cấp một). */
    static final int GIU_TOI_DA = 10;

    static final String LOI_CHUNG = "Điện thoại này chưa từng đăng nhập tài khoản đó nên không tự đặt "
            + "lại mật khẩu được. Nhờ Admin đặt lại mật khẩu cho bạn trên Web Admin.";

    private static final SecureRandom NGAU_NHIEN = new SecureRandom();

    private final KhoaKhoiPhucRepository khoaRepository;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final PhienDangNhapService phienDangNhapService;

    /** Cấp khóa mới cho điện thoại vừa đăng nhập; trả về khóa thật (chỉ lần này). */
    @Transactional
    public String capKhoa(User user, String thietBi) {
        byte[] b = new byte[32];
        NGAU_NHIEN.nextBytes(b);
        String khoa = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        khoaRepository.save(KhoaKhoiPhuc.builder()
                .user(user)
                .maBam(bam(khoa))
                .thietBi(thietBi == null ? null : thietBi.substring(0, Math.min(thietBi.length(), 200)))
                .build());

        List<KhoaKhoiPhuc> ds = khoaRepository.cuaNhanSu(user.getId());
        if (ds.size() > GIU_TOI_DA) khoaRepository.deleteAll(ds.subList(GIU_TOI_DA, ds.size()));
        return khoa;
    }

    /**
     * App đang đăng nhập hỏi lại khóa của mình: khóa máy đang giữ còn hiệu lực
     * thì trả lại đúng khóa đó, không thì cấp khóa mới. Dùng cho máy cài bản cũ
     * (chưa có khóa) và máy có khóa bị gỡ khi chủ tài khoản gỡ một thiết bị khác.
     */
    @Transactional
    public String giuHoacCapMoi(Long userId, String khoaHienCo, String thietBi) {
        if (khoaHienCo != null && !khoaHienCo.isBlank()) {
            String khoa = khoaHienCo.trim();
            var k = khoaRepository.timTheoMaBam(bam(khoa));
            if (k.isPresent() && userId.equals(k.get().getUser().getId())) return khoa;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân sự"));
        return capKhoa(user, thietBi);
    }

    /** Đặt lại mật khẩu về mặc định khi đưa ra đúng số điện thoại + khóa của máy. */
    @Transactional
    public void datLaiMatKhau(String soDienThoai, String khoa) {
        String sdt = soDienThoai == null ? "" : soDienThoai.trim();
        if (sdt.isEmpty() || khoa == null || khoa.isBlank()) throw new RuntimeException(LOI_CHUNG);

        KhoaKhoiPhuc k = khoaRepository.timTheoMaBam(bam(khoa.trim()))
                .orElseThrow(() -> new RuntimeException(LOI_CHUNG));
        User user = k.getUser();
        if (!sdt.equals(user.getPhoneNumber())) throw new RuntimeException(LOI_CHUNG);
        if (!"ACTIVE".equals(user.getStatus()))
            throw new RuntimeException("Tài khoản đã bị khóa. Liên hệ Admin để mở lại.");

        user.setPasswordHash(passwordEncoder.encode(MAT_KHAU_MAC_DINH));
        userRepository.save(user);

        // Đá mọi máy ra như khi đổi mật khẩu, nhưng giữ khóa của máy vừa đặt lại:
        // lỡ chưa kịp đăng nhập lại thì vẫn đặt lại được lần nữa.
        phienDangNhapService.dangXuatMoiThietBi(user.getId(), k.getId());
        k.setLanDungCuoi(ZonedDateTime.now());
        khoaRepository.save(k);
        log.info("[QuenMatKhau] Nhân sự {} tự đặt lại mật khẩu về mặc định (khóa {})", user.getId(), k.getId());
    }

    static String bam(String khoa) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(khoa.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
