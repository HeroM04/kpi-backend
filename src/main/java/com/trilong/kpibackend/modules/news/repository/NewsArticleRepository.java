package com.trilong.kpibackend.modules.news.repository;

import com.trilong.kpibackend.modules.news.entity.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {
    List<NewsArticle> findAllByOrderByPublishedAtDesc();
    long countByCategoryId(Long categoryId);
}
