package com.trilong.kpibackend.modules.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Ngày nghỉ lễ của cả công ty (Tết, Quốc khánh, ngày nghỉ toàn quốc đột xuất…).
 *
 * <p>Ngày lễ thì tác vụ chốt cuối ngày KHÔNG ghi vắng không phép (−15đ) cho ai
 * cả, và bảng công tháng ghi "L". Trước đây hệ thống không có khái niệm này nên
 * ngày nghỉ toàn quốc 24/11/2026 cả công ty bị chấm vắng không phép.
 */
@Entity
@Table(name = "ngay_nghi_le")
@Data
@NoArgsConstructor
public class NgayNghiLe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ngay", nullable = false, unique = true)
    private LocalDate ngay;

    /** Tên ngày lễ hiện trên bảng công, ví dụ "Tết Dương lịch". */
    @Column(name = "ten", length = 200)
    private String ten;

    @Column(name = "tao_luc")
    private ZonedDateTime taoLuc;

    public NgayNghiLe(LocalDate ngay, String ten) {
        this.ngay = ngay;
        this.ten = ten;
        this.taoLuc = ZonedDateTime.now();
    }
}
