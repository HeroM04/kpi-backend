package com.trilong.kpibackend.modules.news.dto;

import lombok.Data;

@Data
public class NewsCategoryDTO {
    private Long id;
    private String name;
    private String slug;
    private Integer sortOrder;
    private long articleCount; // số bài thuộc chuyên mục (tính động)
}
