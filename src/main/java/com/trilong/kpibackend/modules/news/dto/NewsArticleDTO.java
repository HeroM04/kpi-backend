package com.trilong.kpibackend.modules.news.dto;

import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;

@Data
public class NewsArticleDTO {
    private Long id;
    private String title;
    private String slug;
    private String thumbnail;
    private String summary;
    private String content;
    private String author;
    private Long categoryId;
    private String categoryName;
    private List<String> tags;
    private Long projectId;
    private ZonedDateTime publishedAt;
    private Integer viewCount;
    private String status;
}
