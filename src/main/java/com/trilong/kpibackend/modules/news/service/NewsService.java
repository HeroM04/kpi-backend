package com.trilong.kpibackend.modules.news.service;

import com.trilong.kpibackend.modules.news.dto.NewsArticleDTO;
import com.trilong.kpibackend.modules.news.dto.NewsCategoryDTO;
import com.trilong.kpibackend.modules.news.entity.NewsArticle;
import com.trilong.kpibackend.modules.news.entity.NewsCategory;
import com.trilong.kpibackend.modules.news.repository.NewsArticleRepository;
import com.trilong.kpibackend.modules.news.repository.NewsCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class NewsService {

    @Autowired
    private NewsArticleRepository articleRepository;

    @Autowired
    private NewsCategoryRepository categoryRepository;

    /**
     * Danh sách bài viết có lọc (chuyên mục / từ khoá / dự án).
     * projectId != null -> dùng cho sub-tab "Tin tức dự án".
     */
    @Transactional(readOnly = true)
    public List<NewsArticleDTO> getArticles(Long categoryId, String q, Long projectId) {
        String keyword = q == null ? null : q.trim().toLowerCase();
        return articleRepository.findAllByOrderByPublishedAtDesc().stream()
                .filter(a -> categoryId == null
                        || (a.getCategory() != null && categoryId.equals(a.getCategory().getId())))
                .filter(a -> projectId == null || projectId.equals(a.getProjectId()))
                .filter(a -> keyword == null || keyword.isEmpty()
                        || (a.getTitle() != null && a.getTitle().toLowerCase().contains(keyword))
                        || (a.getSummary() != null && a.getSummary().toLowerCase().contains(keyword)))
                .map(this::toArticleDTO)
                .toList();
    }

    @Transactional
    public NewsArticleDTO getById(Long id) {
        NewsArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("News article not found with ID: " + id));
        article.setViewCount(article.getViewCount() == null ? 1 : article.getViewCount() + 1);
        return toArticleDTO(articleRepository.save(article));
    }

    @Transactional(readOnly = true)
    public List<NewsCategoryDTO> getCategories() {
        return categoryRepository.findAllByOrderBySortOrderAsc().stream()
                .map(this::toCategoryDTO)
                .toList();
    }

    /** Tập hợp các thẻ (tag) duy nhất từ toàn bộ bài viết — cho widget "Thẻ". */
    @Transactional(readOnly = true)
    public List<String> getTags() {
        Set<String> tags = new LinkedHashSet<>();
        articleRepository.findAllByOrderByPublishedAtDesc().forEach(a -> {
            if (a.getTags() != null) tags.addAll(a.getTags());
        });
        return new ArrayList<>(tags);
    }

    @Transactional
    public NewsArticleDTO createArticle(NewsArticleDTO dto) {
        NewsArticle article = new NewsArticle();
        mapArticleDtoToEntity(dto, article);
        return toArticleDTO(articleRepository.save(article));
    }

    @Transactional
    public NewsArticleDTO updateArticle(Long id, NewsArticleDTO dto) {
        NewsArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("News article not found with ID: " + id));
        mapArticleDtoToEntity(dto, article);
        return toArticleDTO(articleRepository.save(article));
    }

    @Transactional
    public void deleteArticle(Long id) {
        articleRepository.deleteById(id);
    }

    @Transactional
    public NewsCategoryDTO createCategory(NewsCategoryDTO dto) {
        NewsCategory category = new NewsCategory();
        category.setName(dto.getName());
        category.setSlug(dto.getSlug() != null ? dto.getSlug() : generateSlug(dto.getName()));
        category.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        return toCategoryDTO(categoryRepository.save(category));
    }

    @Transactional
    public NewsCategoryDTO updateCategory(Long id, NewsCategoryDTO dto) {
        NewsCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with ID: " + id));
        category.setName(dto.getName());
        if (dto.getSlug() != null) category.setSlug(dto.getSlug());
        if (dto.getSortOrder() != null) category.setSortOrder(dto.getSortOrder());
        return toCategoryDTO(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }

    private void mapArticleDtoToEntity(NewsArticleDTO dto, NewsArticle article) {
        article.setTitle(dto.getTitle());
        if (dto.getSlug() != null) {
            article.setSlug(dto.getSlug());
        } else if (article.getSlug() == null && dto.getTitle() != null) {
            article.setSlug(generateSlug(dto.getTitle()));
        }
        article.setThumbnail(dto.getThumbnail());
        article.setSummary(dto.getSummary());
        article.setContent(dto.getContent());
        article.setAuthor(dto.getAuthor());
        if (dto.getCategoryId() != null) {
            NewsCategory category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));
            article.setCategory(category);
        } else {
            article.setCategory(null);
        }
        article.setTags(dto.getTags());
        article.setProjectId(dto.getProjectId());
        article.setPublishedAt(dto.getPublishedAt());
        article.setStatus(dto.getStatus());
        if (dto.getViewCount() != null) {
            article.setViewCount(dto.getViewCount());
        } else if (article.getViewCount() == null) {
            article.setViewCount(0);
        }
    }

    private String generateSlug(String input) {
        if (input == null) return null;
        String slug = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug + "-" + System.currentTimeMillis();
    }

    private NewsArticleDTO toArticleDTO(NewsArticle a) {
        NewsArticleDTO dto = new NewsArticleDTO();
        dto.setId(a.getId());
        dto.setTitle(a.getTitle());
        dto.setSlug(a.getSlug());
        dto.setThumbnail(a.getThumbnail());
        dto.setSummary(a.getSummary());
        dto.setContent(a.getContent());
        dto.setAuthor(a.getAuthor());
        if (a.getCategory() != null) {
            dto.setCategoryId(a.getCategory().getId());
            dto.setCategoryName(a.getCategory().getName());
        }
        dto.setTags(a.getTags());
        dto.setProjectId(a.getProjectId());
        dto.setPublishedAt(a.getPublishedAt());
        dto.setViewCount(a.getViewCount());
        dto.setStatus(a.getStatus());
        return dto;
    }

    private NewsCategoryDTO toCategoryDTO(NewsCategory c) {
        NewsCategoryDTO dto = new NewsCategoryDTO();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setSlug(c.getSlug());
        dto.setSortOrder(c.getSortOrder());
        dto.setArticleCount(articleRepository.countByCategoryId(c.getId()));
        return dto;
    }
}
