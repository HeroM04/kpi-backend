package com.trilong.kpibackend.modules.salepro.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;

/**
 * Chuyên viên / quản lý quỹ căn — hiển thị ở card "Quản lý" trong modal chi tiết căn
 * (vd "Dương Hồng Hạnh MC" + SĐT + chat). Gắn vào Project (managingAgent), đổ xuống từng căn.
 */
@Entity
@Table(name = "sales_agents", schema = "salepro")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesAgent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 100)
    private String title; // vd "MC", "Quản lý quỹ căn"

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "zalo_link", length = 500)
    private String zaloLink;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
