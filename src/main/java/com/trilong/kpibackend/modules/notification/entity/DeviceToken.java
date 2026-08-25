package com.trilong.kpibackend.modules.notification.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * DeviceToken — Mã thiết bị Firebase của một nhân sự, để đẩy thông báo tới máy.
 *
 * <p>Mỗi lần đăng nhập, ứng dụng gửi lên một mã (FCM token) đại diện cho máy
 * đang dùng. Máy chủ lưu lại; khi cần báo tin thì gửi cho Firebase kèm mã này,
 * Firebase đẩy tiếp xuống đúng máy.
 *
 * <p>Một người có thể đăng nhập trên nhiều máy, và mã có thể đổi theo thời gian,
 * nên khóa duy nhất đặt trên chính mã token chứ không phải trên người dùng.
 */
@Entity
@Table(name = "device_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_token", columnNames = "token"),
        indexes = @Index(name = "idx_device_user", columnList = "user_id"))
@Data
@NoArgsConstructor
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Mã FCM — dài, nên để TEXT thay vì varchar. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String token;

    /** ios hoặc android — để sau này cần lọc theo nền tảng. */
    @Column(length = 10)
    private String platform;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        this.updatedAt = ZonedDateTime.now();
    }
}
