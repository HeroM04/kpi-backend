package com.trilong.kpibackend.modules.salepro.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Tài liệu dự án — tab "Tài liệu". Mỗi mục là link Google Drive (bấm vào mở thẳng Drive).
 */
@Entity
@Table(name = "project_documents", schema = "salepro")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(length = 150)
    private String label; // vd "TỔNG MẶT BẰNG", "LAYOUT CĂN HỘ", "PHÁP LÝ DỰ ÁN", "CSBH", "TRỤC CĂN"

    @Column(name = "drive_url", length = 1000)
    private String driveUrl;

    @Column(name = "doc_type", length = 50)
    private String docType; // vd PDF, VIDEO, SLIDE, IMAGE

    @Column(name = "sort_order")
    private Integer sortOrder;
}
