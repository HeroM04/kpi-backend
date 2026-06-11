package com.trilong.kpibackend.modules.news.repository;

import com.trilong.kpibackend.modules.news.entity.NewsCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewsCategoryRepository extends JpaRepository<NewsCategory, Long> {
    List<NewsCategory> findAllByOrderBySortOrderAsc();
}
