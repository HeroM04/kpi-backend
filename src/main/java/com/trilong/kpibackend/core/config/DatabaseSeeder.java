package com.trilong.kpibackend.core.config;

import com.trilong.kpibackend.modules.user.entity.Department;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.DepartmentRepository;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * DatabaseSeeder — tự động nạp dữ liệu mẫu cho phòng ban và người dùng
 * khi ứng dụng khởi chạy lần đầu nếu cơ sở dữ liệu trống.
 */
@Component
public class DatabaseSeeder implements CommandLineRunner {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (departmentRepository.count() == 0) {
            seedDepartmentsAndUsers();
        }
    }

    private void seedDepartmentsAndUsers() {
        System.out.println("====== [DATABASE SEEDER] Khởi tạo dữ liệu mẫu bắt đầu ======");

        // 1. Khởi tạo Departments
        Department kd1 = Department.builder()
                .name("Phòng Kinh Doanh 1")
                .officeLat(21.028511)
                .officeLng(105.804817)
                .allowedRadius(100)
                .build();

        Department kd2 = Department.builder()
                .name("Phòng Kinh Doanh 2")
                .officeLat(21.028511)
                .officeLng(105.804817)
                .allowedRadius(100)
                .build();

        Department hr = Department.builder()
                .name("Phòng Hành Chính - HR")
                .officeLat(21.028511)
                .officeLng(105.804817)
                .allowedRadius(100)
                .build();

        departmentRepository.saveAll(List.of(kd1, kd2, hr));
        System.out.println("[DATABASE SEEDER] Đã tạo 3 phòng ban thành công.");

        // 2. Khởi tạo Users
        String encodedPassword = passwordEncoder.encode("123456");

        User admin = User.builder()
                .fullName("Nguyễn Thị Admin")
                .phoneNumber("0900000001")
                .passwordHash(encodedPassword)
                .role("ADMIN")
                .status("ACTIVE")
                .department(hr)
                .build();

        User manager = User.builder()
                .fullName("Trần Văn Trưởng Phòng")
                .phoneNumber("0900000002")
                .passwordHash(encodedPassword)
                .role("TRUONG_PHONG")
                .status("ACTIVE")
                .department(kd1)
                .build();

        User saleA = User.builder()
                .fullName("Lê Thị Sale A")
                .phoneNumber("0900000003")
                .passwordHash(encodedPassword)
                .role("SALE")
                .status("ACTIVE")
                .department(kd1)
                .build();

        User saleB = User.builder()
                .fullName("Phạm Văn Sale B")
                .phoneNumber("0900000004")
                .passwordHash(encodedPassword)
                .role("SALE")
                .status("ACTIVE")
                .department(kd2)
                .build();

        User staff = User.builder()
                .fullName("Hoàng Thị Văn Phòng")
                .phoneNumber("0900000005")
                .passwordHash(encodedPassword)
                .role("VAN_PHONG")
                .status("ACTIVE")
                .department(hr)
                .build();

        userRepository.saveAll(List.of(admin, manager, saleA, saleB, staff));
        System.out.println("[DATABASE SEEDER] Đã tạo 5 tài khoản thử nghiệm thành công (Mật khẩu: 123456).");
        System.out.println("====== [DATABASE SEEDER] Khởi tạo dữ liệu mẫu hoàn thành ======");
    }
}
