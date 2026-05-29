package com.trilong.kpibackend.modules.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

/**
 * Entity User — ánh xạ bảng users trong PostgreSQL.
 * Đăng nhập bằng phone_number (unique).
 * Role: SALE | ADMIN | TRUONG_PHONG | VAN_PHONG
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Quan hệ ManyToOne với phòng ban — lấy tọa độ văn phòng từ department
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    // Dùng số điện thoại để đăng nhập (unique, không được để trống)
    @Column(name = "phone_number", unique = true, nullable = false)
    private String phoneNumber;

    // Mật khẩu luôn được lưu dạng BCrypt hash, KHÔNG BAO GIỜ lưu plain text
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * Role của nhân viên:
     * - SALE          : Nhân viên kinh doanh (Sales)
     * - ADMIN         : Quản trị viên / HR
     * - TRUONG_PHONG  : Trưởng phòng kinh doanh (TPKD)
     * - VAN_PHONG     : Nhân viên văn phòng (Admin/Kế toán/...)
     */
    @Column(length = 50)
    @Builder.Default
    private String role = "SALE";

    // ACTIVE | INACTIVE | SUSPENDED
    @Column(length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    // Ảnh đại diện gốc dùng cho điểm danh bằng nhận diện khuôn mặt (AWS Rekognition)
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    // Lương cứng mặc định của nhân viên
    @Column(name = "basic_salary")
    @Builder.Default
    private Double basicSalary = 10000000.0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.role == null) this.role = "SALE";
        if (this.status == null) this.status = "ACTIVE";
        if (this.basicSalary == null) this.basicSalary = 10000000.0;
    }
}
