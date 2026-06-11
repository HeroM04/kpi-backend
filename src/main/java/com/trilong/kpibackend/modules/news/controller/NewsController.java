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

    @Operation(summary = "Tạo bài viết mới")
    @PostMapping
    public ResponseEntity<?> createArticle(@RequestBody NewsArticleDTO dto) {
        NewsArticleDTO created = newsService.createArticle(dto);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", created));
    }

    @Operation(summary = "Cập nhật bài viết")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateArticle(@PathVariable Long id, @RequestBody NewsArticleDTO dto) {
        NewsArticleDTO updated = newsService.updateArticle(id, dto);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", updated));
    }

    @Operation(summary = "Xóa bài viết")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteArticle(@PathVariable Long id) {
        newsService.deleteArticle(id);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Deleted successfully"));
    }

    @Operation(summary = "Tạo chuyên mục mới")
    @PostMapping("/categories")
    public ResponseEntity<?> createCategory(@RequestBody NewsCategoryDTO dto) {
        NewsCategoryDTO created = newsService.createCategory(dto);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", created));
    }

    @Operation(summary = "Cập nhật chuyên mục")
    @PutMapping("/categories/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable Long id, @RequestBody NewsCategoryDTO dto) {
        NewsCategoryDTO updated = newsService.updateCategory(id, dto);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", updated));
    }

    @Operation(summary = "Xóa chuyên mục")
    @DeleteMapping("/categories/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        newsService.deleteCategory(id);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Deleted successfully"));
    }
}
