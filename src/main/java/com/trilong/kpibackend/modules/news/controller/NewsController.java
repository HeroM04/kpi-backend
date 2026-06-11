package com.trilong.kpibackend.modules.news.controller;

import com.trilong.kpibackend.modules.news.dto.NewsArticleDTO;
import com.trilong.kpibackend.modules.news.dto.NewsCategoryDTO;
import com.trilong.kpibackend.modules.news.service.NewsService;
import com.trilong.kpibackend.core.utils.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/news")
@Tag(name = "News API", description = "Tin tức cổng SaleWeb (Trí Long Land)")
public class NewsController {

    @Autowired
    private NewsService newsService;

    @Operation(summary = "Danh sách bài viết (lọc chuyên mục / từ khoá / dự án + phân trang)")
    @GetMapping
    public ResponseEntity<?> getArticles(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size) {
        List<NewsArticleDTO> articles = newsService.getArticles(categoryId, q, projectId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", PageResponse.fromList(articles, page, size)
        ));
    }

    @Operation(summary = "Danh sách chuyên mục (kèm số bài)")
    @GetMapping("/categories")
    public ResponseEntity<?> getCategories() {
        List<NewsCategoryDTO> categories = newsService.getCategories();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", categories
        ));
    }

    @Operation(summary = "Danh sách thẻ (tag)")
    @GetMapping("/tags")
    public ResponseEntity<?> getTags() {
        List<String> tags = newsService.getTags();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", tags
        ));
    }

    @Operation(summary = "Chi tiết một bài viết")
    @GetMapping("/{id}")
    public ResponseEntity<?> getArticleById(@PathVariable Long id) {
        NewsArticleDTO article = newsService.getById(id);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", article
        ));
    }
}
