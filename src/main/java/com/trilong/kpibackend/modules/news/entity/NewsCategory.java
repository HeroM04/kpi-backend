package com.trilong.kpibackend.modules.news.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Chuyên mục tin tức (sidebar "Chuyên mục" — kèm số bài). Vd "Phân Tích - Nhận định", "Tin Tức Dự Án".
 */
@Entity
@Table(name = "news_categories", schema = "salepro")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(unique = true, length = 180)
    private String slug;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
