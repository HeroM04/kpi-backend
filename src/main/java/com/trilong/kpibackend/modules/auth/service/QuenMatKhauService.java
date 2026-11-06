package com.trilong.kpibackend.modules.auth.service;

import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nhân sự quên mật khẩu tự đặt lại về {@value #MAT_KHAU_MAC_DINH} ngay trên màn
 * đăng nhập app, chỉ cần số điện thoại (theo yêu cầu: không cần xác minh gì thêm).
 *
 * <p>Không đá các máy đang đăng nhập: người quên mật khẩu vốn đã ở màn đăng nhập,
 * còn ai bấm nhầm số người khác thì người đó vẫn dùng app bình thường — lần đăng
 * nhập sau mới cần mật khẩu mới.
 *
 * <p>Trừ tài khoản Admin: Web Admin có hoa hồng, doanh thu — ai biết số Admin mà
 * đặt lại được rồi đăng nhập bằng 123456 thì xem được hết. Admin quên mật khẩu
 * thì nhờ Admin khác đặt lại trên Web Admin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuenMatKhauService {

    public static final String MAT_KHAU_MAC_DINH = "123456";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Transactional
    public void datLaiMatKhau(String soDienThoai) {
        String sdt = soDienThoai == null ? "" : soDienThoai.trim();
        if (sdt.isEmpty()) throw new RuntimeException("Nhập số điện thoại của tài khoản cần đặt lại mật khẩu.");

        User user = userRepository.findByPhoneNumber(sdt)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản với số điện thoại này"));
        if (!"ACTIVE".equals(user.getStatus()))
            throw new RuntimeException("Tài khoản đã bị khóa. Liên hệ Admin để mở lại.");
        if ("ADMIN".equals(user.getRole()))
            throw new RuntimeException("Tài khoản Admin không tự đặt lại mật khẩu được. "
                    + "Nhờ Admin khác đặt lại trên Web Admin.");

        user.setPasswordHash(passwordEncoder.encode(MAT_KHAU_MAC_DINH));
        userRepository.save(user);
        log.info("[QuenMatKhau] Nhân sự {} tự đặt lại mật khẩu về mặc định", user.getId());
    }
}
