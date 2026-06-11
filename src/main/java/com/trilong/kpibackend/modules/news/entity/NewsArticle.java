package com.trilong.kpibackend.modules.news.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Bài viết tin tức. Có thể gắn với 1 dự án (projectId) để hiển thị ở sub-tab "Tin tức dự án".
 */
@Entity
@Table(name = "news_articles", schema = "salepro")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(unique = true, length = 255)
    private String slug;

    @Column(length = 500)
    private String thumbnail;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String content; // nội dung HTML

    @Column(length = 150)
    private String author; // vd "Mayhomes"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private NewsCategory category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> tags;

    @Column(name = "project_id")
    private Long projectId; // nullable — gắn bài vào dự án (sub-tab tin tức dự án)

    @Column(name = "published_at")
    private ZonedDateTime publishedAt;

    @Column(name = "view_count")
    private Integer viewCount;

    @Column(length = 30)
    private String status; // PUBLISHED, DRAFT

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
