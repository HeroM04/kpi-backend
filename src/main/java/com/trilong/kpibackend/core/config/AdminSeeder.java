package com.trilong.kpibackend.core.config;

import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

/**
 * Tạo tài khoản quản trị đầu tiên khi DB còn trống, để có người đăng nhập
 * WebAdmin mà tạo phòng ban và nhân sự.
 *
 * Mật khẩu lấy từ biến môi trường ADMIN_INITIAL_PASSWORD. Không đặt thì sinh
 * ngẫu nhiên và in ra log MỘT LẦN — trước đây ghi cứng "admin123" trong mã
 * nguồn (kho công khai) nghĩa là ai đọc GitHub cũng biết mật khẩu admin của
 * mọi bản cài mới.
 */
@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_INITIAL_PASSWORD:}")
    private String matKhauBanDau;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByPhoneNumber("admin")) return;

        String mk = (matKhauBanDau != null && !matKhauBanDau.isBlank()) ? matKhauBanDau.trim() : sinhMatKhau();
        User admin = User.builder()
                .phoneNumber("admin")
                .passwordHash(passwordEncoder.encode(mk))
                .fullName("System Admin")
                .role("ADMIN")
                .status("ACTIVE")
                .build();
        userRepository.save(admin);

        System.out.println("====== SYSTEM ADMIN CREATED ======");
        System.out.println("Username (Phone): admin");
        if (matKhauBanDau != null && !matKhauBanDau.isBlank()) {
            System.out.println("Password: (theo biến ADMIN_INITIAL_PASSWORD)");
        } else {
            System.out.println("Password (tự sinh, chỉ in một lần — đổi ngay sau khi đăng nhập): " + mk);
        }
        System.out.println("==================================");
    }

    /** 12 ký tự chữ + số, tránh ký tự dễ nhìn nhầm (0/O, 1/l/I). */
    private static String sinhMatKhau() {
        final String bang = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(bang.charAt(r.nextInt(bang.length())));
        return sb.toString();
    }
}
