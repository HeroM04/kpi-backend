package com.trilong.kpibackend.core.utils.dev;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Công cụ tạo BCrypt hash cho mật khẩu — chỉ dùng trong môi trường DEV.
 * Chạy main() để lấy hash, rồi paste vào SQL hoặc seed data.
 *
 * Cách chạy:
 *   mvn exec:java -Dexec.mainClass="com.trilong.kpibackend.core.utils.dev.PasswordHashGenerator"
 *
 * KHÔNG deploy class này lên production.
 */
public class PasswordHashGenerator {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String[] passwords = {"123456", "Admin@123", "Sale@123", "Trilong@2024"};

        System.out.println("=== BCrypt Password Hash Generator ===");
        for (String pwd : passwords) {
            String hash = encoder.encode(pwd);
            System.out.println("Password : " + pwd);
            System.out.println("Hash     : " + hash);
            System.out.println("Verify   : " + encoder.matches(pwd, hash));
            System.out.println("---");
        }
    }
}
