package com.trilong.kpibackend.modules.auth.service;

import com.trilong.kpibackend.core.security.HoSoNongService;
import com.trilong.kpibackend.modules.auth.entity.RefreshToken;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.auth.repository.RefreshTokenRepository;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý phiên đăng nhập: xem máy nào đang dùng tài khoản, và đá hết ra khi cần.
 *
 * <p><b>Vì sao cần:</b> đăng nhập ở máy người khác rồi quên đăng xuất thì trình
 * duyệt đó vẫn giữ token. Thu hồi refresh token thôi chưa đủ — access token là
 * JWT tự chứng thực, máy chủ không tra DB nên nó vẫn dùng được tới khi hết hạn.
 * Vì vậy "đăng xuất mọi thiết bị" làm hai việc: thu hồi toàn bộ refresh token
 * VÀ đặt mốc {@code sessionsValidFrom} để {@code JwtAuthFilter} chặn ngay mọi
 * access token đã phát trước đó.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhienDangNhapService {

    /** Một phiên đăng nhập, đã bỏ token thật đi — token không bao giờ rời máy chủ. */
    public record Phien(Long id, String thietBi, String ipAddress,
                        ZonedDateTime dangNhapLuc, ZonedDateTime hoatDongCuoi,
                        ZonedDateTime hetHanLuc, boolean phienHienTai) {}

    /** Ghi "còn hoạt động" nhiều nhất một lần mỗi phút cho mỗi phiên. */
    private static final long NHIP_GHI_MS = 60_000;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final HoSoNongService hoSoNong;
    private final GhiHoatDongNen ghiNen;

    private final Map<Long, Long> lanGhiCuoi = new ConcurrentHashMap<>();

    /**
     * Danh sách phiên còn hiệu lực của một người.
     *
     * @param phienHienTai id phiên của người đang xem, để đánh dấu "máy này" —
     *                     null nếu không biết (token cũ chưa có claim sid)
     */
    public List<Phien> danhSach(Long userId, Long phienHienTai) {
        return refreshTokenRepository.timPhienConHieuLuc(userId, ZonedDateTime.now())
                .stream()
                .map(rt -> new Phien(rt.getId(), moTaThietBi(rt.getDeviceInfo()), rt.getIpAddress(),
                        rt.getCreatedAt(), rt.getLastSeenAt(), rt.getExpiresAt(),
                        phienHienTai != null && phienHienTai.equals(rt.getId())))
                .toList();
    }

    /**
     * Đăng xuất khỏi MỌI thiết bị của một người và bắt đăng nhập lại.
     *
     * @return số phiên vừa bị cắt
     */
    @Transactional
    public int dangXuatMoiThietBi(Long userId) {
        int soPhien = refreshTokenRepository.thuHoiTatCa(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân sự"));
        // Cộng thêm một giây: token phát ra trong CÙNG giây này (làm tròn xuống ở
        // trường iat của JWT) cũng phải bị chặn, không thì lọt đúng cái token vừa cấp.
        user.setSessionsValidFrom(ZonedDateTime.now().plusSeconds(1));
        userRepository.save(user);
        hoSoNong.quen(userId);
        lanGhiCuoi.clear();
        log.info("[Phien] Đã đăng xuất mọi thiết bị của nhân sự {} ({} phiên)", userId, soPhien);
        return soPhien;
    }

    /** Thu hồi đúng một phiên (một máy), các máy khác giữ nguyên. */
    @Transactional
    public boolean thuHoi(Long userId, Long phienId) {
        boolean xong = refreshTokenRepository.thuHoiMotPhien(phienId, userId) > 0;
        if (xong) {
            hoSoNong.quen(userId);   // để yêu cầu kế tiếp của máy đó bị chặn ngay
            lanGhiCuoi.remove(phienId);
        }
        return xong;
    }

    /**
     * Ghi dấu phiên vừa gọi máy chủ.
     *
     * <p>Chạy trên luồng của chính yêu cầu nhưng gần như luôn thoát ngay ở dòng
     * kiểm tra nhịp: mỗi phiên chỉ ghi một lần mỗi phút. Không có cái chặn đó thì
     * mỗi lần bấm một nút trên web là một lệnh UPDATE — DB gói miễn phí không
     * chịu nổi. Đúng lượt phải ghi thì đẩy sang luồng nền để yêu cầu không phải
     * chờ DB.
     */
    public void ghiNhanHoatDong(Long phienId) {
        if (phienId == null) return;
        long bayGio = System.currentTimeMillis();
        Long truoc = lanGhiCuoi.get(phienId);
        if (truoc != null && bayGio - truoc < NHIP_GHI_MS) return;
        lanGhiCuoi.put(phienId, bayGio);
        ghiNen.ghi(phienId);
    }

    /**
     * Tách riêng một bean để {@code @Async} có hiệu lực — gọi thẳng một phương
     * thức async trong cùng lớp thì Spring không chen proxy vào được, nó chạy
     * đồng bộ như thường.
     */
    @Slf4j
    @org.springframework.stereotype.Component
    @RequiredArgsConstructor
    public static class GhiHoatDongNen {
        private final RefreshTokenRepository refreshTokenRepository;

        @Async
        @Transactional
        public void ghi(Long phienId) {
            try {
                refreshTokenRepository.ghiNhanHoatDong(phienId, ZonedDateTime.now());
            } catch (Exception e) {
                log.debug("[Phien] Không ghi được hoạt động của phiên {}: {}", phienId, e.getMessage());
            }
        }
    }

    /**
     * Rút gọn chuỗi User-Agent thành thứ người đọc được: "Chrome trên Windows",
     * "App Android"… Chuỗi gốc dài cả trăm ký tự, để nguyên thì bảng không đọc nổi.
     */
    static String moTaThietBi(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "Không rõ thiết bị";
        String ua = userAgent.toLowerCase();

        if (ua.contains("dart") || ua.contains("flutter")) {
            return ua.contains("android") ? "App Trí Long (Android)"
                 : ua.contains("ios") || ua.contains("darwin") ? "App Trí Long (iPhone)"
                 : "App Trí Long";
        }

        String trinhDuyet = ua.contains("edg/") ? "Microsoft Edge"
                : ua.contains("opr/") || ua.contains("opera") ? "Opera"
                : ua.contains("chrome") ? "Chrome"
                : ua.contains("firefox") ? "Firefox"
                : ua.contains("safari") ? "Safari"
                : "Trình duyệt khác";

        String heDieuHanh = ua.contains("windows") ? "Windows"
                : ua.contains("android") ? "Android"
                : ua.contains("iphone") || ua.contains("ipad") ? "iPhone/iPad"
                : ua.contains("mac os") || ua.contains("macintosh") ? "macOS"
                : ua.contains("linux") ? "Linux"
                : null;

        return heDieuHanh == null ? trinhDuyet : trinhDuyet + " trên " + heDieuHanh;
    }
}
